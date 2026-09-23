package com.techpix.fraud.internal;

import com.techpix.fraud.FraudProfile;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param rejectThreshold   score a partir do qual o pagamento é rejeitado
 * @param profile           perfil inicial de regras
 * @param providerLatencyMs latência simulada do provider externo de fraude (perfil HEAVY)
 * @param mlIterations      iterações do "modelo de ML" simulado; controla o custo de CPU (perfil HEAVY)
 */
@ConfigurationProperties(prefix = "techpix.fraud")
public record FraudProperties(
        @DefaultValue("70") int rejectThreshold,
        @DefaultValue("SIMPLE") FraudProfile profile,
        @DefaultValue("30") long providerLatencyMs,
        @DefaultValue("200000") int mlIterations) {
}
