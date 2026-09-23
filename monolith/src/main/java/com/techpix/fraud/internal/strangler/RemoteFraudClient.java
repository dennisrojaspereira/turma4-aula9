package com.techpix.fraud.internal.strangler;

import com.techpix.fraud.FraudCheck;
import com.techpix.fraud.FraudDecision;
import com.techpix.fraud.internal.FraudProperties;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Cliente HTTP do Fraud Service. Este é o contrato entre os dois processos.
 * <p>
 * Repare no que apareceu e que não existia numa chamada de método: URL, timeouts, serialização.
 */
@Component
public class RemoteFraudClient {

    /** O que Payment envia. Só o necessário para decidir. */
    public record EvaluationRequest(UUID paymentId, UUID payerAccountId, UUID payeeAccountId,
                                    BigDecimal amount, String deviceId, Instant occurredAt) {
        static EvaluationRequest from(FraudCheck check) {
            return new EvaluationRequest(check.paymentId(), check.payerAccountId(), check.payeeAccountId(),
                    check.amount(), check.deviceId(), check.occurredAt());
        }
    }

    /** O que o Fraud Service devolve. */
    public record EvaluationResponse(UUID paymentId, int score, FraudDecision decision,
                                     List<String> triggeredRules, int rulesEvaluated, long durationMs) {
    }

    private final RestClient http;

    public RemoteFraudClient(RestClient.Builder builder, FraudProperties properties) {
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.defaults()
                .withConnectTimeout(Duration.ofMillis(properties.remote().connectTimeoutMs()))
                .withReadTimeout(Duration.ofMillis(properties.remote().readTimeoutMs()));
        this.http = builder
                .baseUrl(properties.remote().url())
                .requestFactory(ClientHttpRequestFactoryBuilder.simple().build(settings))
                .build();
    }

    public EvaluationResponse evaluate(FraudCheck check) {
        return http.post()
                .uri("/fraud/evaluations")
                .contentType(MediaType.APPLICATION_JSON)
                .body(EvaluationRequest.from(check))
                .retrieve()
                .body(EvaluationResponse.class);
    }
}
