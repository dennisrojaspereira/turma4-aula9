package com.techpix.observability;

import static org.assertj.core.api.Assertions.assertThat;

import com.techpix.fraud.FraudProfile;
import com.techpix.fraud.FraudProfileConfig;
import com.techpix.support.AbstractIntegrationTest;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;

/**
 * "Quantas consultas custa um pagamento?" Este teste responde com número.
 * É a evidência que transforma "o banco está lento" em "Fraud faz N consultas por pagamento".
 */
class QueryCountIT extends AbstractIntegrationTest {

    @Autowired
    FraudProfileConfig profile;
    @Autowired
    MeterRegistry metrics;

    @AfterEach
    void restore() {
        profile.activate(FraudProfile.SIMPLE);
    }

    @Test
    void simpleProfileCostsAboutADozenQueriesPerPayment() {
        profile.activate(FraudProfile.SIMPLE);
        UUID payer = openAccount("QC Simple", "1000.00");
        UUID payee = openAccount("QC Simple Payee", "0.00");

        ResponseEntity<Map> response = pay(payer, payee, "10.00", "qc-device");

        long queries = ((Number) response.getBody().get("dbQueries")).longValue();
        // 2 contas + insert + 2 consultas de fraude + evaluation + transfer(2) + ledger(2) + status + 2 notificacoes + select final
        assertThat(queries).isBetween(10L, 16L);
    }

    @Test
    void heavyProfileMultipliesQueriesPerPayment() {
        profile.activate(FraudProfile.HEAVY);
        UUID payer = openAccount("QC Heavy", "1000.00");
        UUID payee = openAccount("QC Heavy Payee", "0.00");

        ResponseEntity<Map> response = pay(payer, payee, "10.00", "qc-device-heavy");

        long queries = ((Number) response.getBody().get("dbQueries")).longValue();
        assertThat(queries).isGreaterThanOrEqualTo(25L);
    }

    @Test
    void perRuleTimersAndQueryCountsAreExposedAsMetrics() {
        profile.activate(FraudProfile.HEAVY);
        UUID payer = openAccount("QC Metrics", "1000.00");
        UUID payee = openAccount("QC Metrics Payee", "0.00");
        pay(payer, payee, "10.00", "qc-device-metrics");

        assertThat(metrics.find("fraud.rule").tag("rule", "velocity").timer()).isNotNull();
        assertThat(metrics.find("fraud.rule.queries").tag("rule", "velocity").summary().mean()).isEqualTo(3.0);
        assertThat(metrics.find("fraud.rule.queries").tag("rule", "ml-scoring").summary().mean()).isZero();
        assertThat(metrics.find("payment.create").timer().count()).isGreaterThan(0);
        assertThat(metrics.find("hikaricp.connections.max").gauge()).isNotNull();
    }
}
