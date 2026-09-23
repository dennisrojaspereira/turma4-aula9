package com.techpix.fraud.internal.strangler;

import com.techpix.fraud.FraudCheck;
import com.techpix.fraud.FraudEvaluator;
import com.techpix.fraud.FraudResult;
import com.techpix.fraud.FraudUnavailableException;
import com.techpix.fraud.internal.FraudProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;

/**
 * "NewFraud": delega ao Fraud Service.
 * <p>
 * Uma chamada de método nunca lançava {@code ResourceAccessException}. Uma chamada HTTP lança.
 * <p>
 * Etapa 14: timeout (no cliente), retry limitado com backoff e jitter ({@link RetryPolicy}), e a
 * distinção entre o que vale repetir (rede, timeout, 5xx) e o que não vale (4xx). Qualquer falha
 * depois das tentativas vira {@link FraudUnavailableException}.
 */
@Component
public class RemoteFraudEvaluator implements FraudEvaluator {

    private static final Logger log = LoggerFactory.getLogger(RemoteFraudEvaluator.class);

    private final RemoteFraudClient client;
    private final RetryPolicy retry;
    private final Timer roundTrip;
    private final Counter retries;

    public RemoteFraudEvaluator(RemoteFraudClient client, FraudProperties properties, MeterRegistry metrics) {
        this.client = client;
        FraudProperties.Retry r = properties.remote().retry();
        this.retry = new RetryPolicy(r.maxAttempts(), r.baseBackoffMs(), r.maxBackoffMs(), r.jitter());
        this.roundTrip = Timer.builder("fraud.remote.roundtrip")
                .description("Tempo de ida e volta ate o Fraud Service, incluindo retries").register(metrics);
        this.retries = Counter.builder("fraud.remote.retries").description("Tentativas extras contra o Fraud Service").register(metrics);
    }

    @Override
    public FraudResult evaluate(FraudCheck check) {
        long start = System.nanoTime();
        int[] attempts = {0};
        try {
            RemoteFraudClient.EvaluationResponse response = retry.execute("fraud-service", () -> {
                if (attempts[0]++ > 0) {
                    retries.increment();
                }
                return client.evaluate(check);
            }, RemoteFraudEvaluator::isRetryable);
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            roundTrip.record(elapsedMs, TimeUnit.MILLISECONDS);
            log.debug("fraud remote payment={} score={} decision={} remoteMs={} roundTripMs={} attempts={}",
                    check.paymentId(), response.score(), response.decision(), response.durationMs(), elapsedMs, attempts[0]);
            return new FraudResult(response.score(), response.decision(), response.triggeredRules(),
                    response.rulesEvaluated(), elapsedMs);
        } catch (ResourceAccessException e) {
            throw new FraudUnavailableException("fraud service unreachable after " + attempts[0] + " attempt(s): " + e.getMessage(), e);
        } catch (RestClientException e) {
            throw new FraudUnavailableException("fraud service error after " + attempts[0] + " attempt(s): " + e.getMessage(), e);
        } catch (Exception e) {
            throw new FraudUnavailableException("fraud service failure: " + e.getMessage(), e);
        }
    }

    /**
     * Timeout, conexao recusada e 5xx: vale repetir. 4xx: a requisicao esta errada, repetir nao conserta.
     * <p>
     * Percorre a cadeia de causas: um timeout de leitura chega como {@code RestClientException("Error while
     * extracting response...")} com o {@code SocketTimeoutException} la dentro. Olhar so o tipo de cima
     * deixaria de repetir exatamente o caso mais comum.
     */
    static boolean isRetryable(Exception e) {
        Throwable t = e;
        while (t != null) {
            if (t instanceof HttpClientErrorException) {
                return false;
            }
            if (t instanceof HttpServerErrorException || t instanceof ResourceAccessException || t instanceof java.io.IOException) {
                return true;
            }
            t = t.getCause();
        }
        return false;
    }
}
