package com.techpix.fraud.internal;

import com.techpix.fraud.FraudMode;
import com.techpix.fraud.FraudProfile;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param rejectThreshold   score a partir do qual o pagamento é rejeitado
 * @param profile           perfil inicial de regras do Fraud legado
 * @param mode              modo inicial do Strangler (a feature flag)
 * @param providerLatencyMs latência simulada do provider externo de fraude
 * @param mlIterations      iterações do "modelo de ML" simulado; controla o custo de CPU
 * @param remote            como chegar ao Fraud Service
 * @param parallel          limites do shadow no Parallel Run
 * @param canary            percentual inicial do canário
 */
@ConfigurationProperties(prefix = "techpix.fraud")
public record FraudProperties(
        @DefaultValue("70") int rejectThreshold,
        @DefaultValue("SIMPLE") FraudProfile profile,
        @DefaultValue("LEGACY") FraudMode mode,
        @DefaultValue("30") long providerLatencyMs,
        @DefaultValue("200000") int mlIterations,
        @DefaultValue Remote remote,
        @DefaultValue Parallel parallel,
        @DefaultValue Canary canary) {

    /**
     * @param url              endereço base do Fraud Service
     * @param connectTimeoutMs quanto esperar para abrir a conexão
     * @param readTimeoutMs    quanto esperar pela resposta
     */
    public record Remote(
            @DefaultValue("http://localhost:8081") String url,
            @DefaultValue("500") long connectTimeoutMs,
            @DefaultValue("2000") long readTimeoutMs) {
    }

    /**
     * @param shadowTimeoutMs quanto esperar pelo shadow antes de registrar TIMEOUT
     * @param threads         threads dedicadas ao shadow (nunca as threads HTTP)
     * @param queueSize       fila do shadow; cheia, descarta e registra SKIPPED
     */
    public record Parallel(
            @DefaultValue("1500") long shadowTimeoutMs,
            @DefaultValue("8") int threads,
            @DefaultValue("100") int queueSize) {
    }

    /** @param percentage 0..100 dos pagamentos decididos pelo Fraud Service em modo CANARY */
    public record Canary(@DefaultValue("0") int percentage) {
    }
}
