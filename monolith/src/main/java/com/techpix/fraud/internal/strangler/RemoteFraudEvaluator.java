package com.techpix.fraud.internal.strangler;

import com.techpix.fraud.FraudCheck;
import com.techpix.fraud.FraudEvaluator;
import com.techpix.fraud.FraudResult;
import com.techpix.fraud.FraudUnavailableException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;

/**
 * "NewFraud": delega ao Fraud Service.
 * <p>
 * Uma chamada de método nunca lançava {@code ResourceAccessException}. Uma chamada HTTP lança.
 * Por enquanto, qualquer falha vira {@link FraudUnavailableException}. Retry, backoff e
 * circuit breaker entram quando houver evidência de que são necessários (lab 20).
 */
@Component
public class RemoteFraudEvaluator implements FraudEvaluator {

    private static final Logger log = LoggerFactory.getLogger(RemoteFraudEvaluator.class);

    private final RemoteFraudClient client;
    private final Timer roundTrip;

    public RemoteFraudEvaluator(RemoteFraudClient client, MeterRegistry metrics) {
        this.client = client;
        this.roundTrip = Timer.builder("fraud.remote.roundtrip")
                .description("Tempo de ida e volta ate o Fraud Service").register(metrics);
    }

    @Override
    public FraudResult evaluate(FraudCheck check) {
        long start = System.nanoTime();
        try {
            RemoteFraudClient.EvaluationResponse response = client.evaluate(check);
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            roundTrip.record(elapsedMs, TimeUnit.MILLISECONDS);
            log.debug("fraud remote payment={} score={} decision={} remoteMs={} roundTripMs={}",
                    check.paymentId(), response.score(), response.decision(), response.durationMs(), elapsedMs);
            return new FraudResult(response.score(), response.decision(), response.triggeredRules(),
                    response.rulesEvaluated(), elapsedMs);
        } catch (ResourceAccessException e) {
            // timeout, conexao recusada, DNS: o servico nao respondeu
            throw new FraudUnavailableException("fraud service unreachable: " + e.getMessage(), e);
        } catch (RestClientException e) {
            // respondeu, mas com erro (5xx, 4xx, corpo invalido)
            throw new FraudUnavailableException("fraud service error: " + e.getMessage(), e);
        }
    }
}
