package com.techpix.fraudservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param rejectThreshold   score a partir do qual a decisão é REJECTED
 * @param providerLatencyMs latência simulada do provider externo
 * @param mlIterations      custo de CPU do modelo simulado
 * @param warmupSeconds     quanto tempo o serviço leva para ficar pronto (carga de cache). Didático: ver lab 12.
 * @param historySource     de onde vem o histórico de pagamentos: LEGACY_SCHEMA (banco compartilhado) ou LOCAL_VIEW
 */
@ConfigurationProperties(prefix = "fraud")
public record FraudServiceProperties(
        @DefaultValue("70") int rejectThreshold,
        @DefaultValue("30") long providerLatencyMs,
        @DefaultValue("200000") int mlIterations,
        @DefaultValue("0") int warmupSeconds,
        @DefaultValue("LEGACY_SCHEMA") HistorySource historySource) {

    public enum HistorySource {
        LEGACY_SCHEMA, LOCAL_VIEW
    }
}
