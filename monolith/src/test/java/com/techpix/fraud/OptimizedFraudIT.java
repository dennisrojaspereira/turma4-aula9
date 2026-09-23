package com.techpix.fraud;

import static org.assertj.core.api.Assertions.assertThat;

import com.techpix.fraud.rules.optimized.BlacklistCache;
import com.techpix.support.AbstractIntegrationTest;
import io.micrometer.core.instrument.MeterRegistry;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Mesma cobertura de risco, custo diferente. O perfil HEAVY_OPTIMIZED deve decidir igual ao HEAVY
 * fazendo uma fração das consultas.
 */
class OptimizedFraudIT extends AbstractIntegrationTest {

    @Autowired
    FraudProfileConfig profile;
    @Autowired
    MeterRegistry metrics;
    @Autowired
    JdbcClient jdbc;
    @Autowired
    BlacklistCache blacklist;

    @AfterEach
    void restore() {
        profile.activate(FraudProfile.SIMPLE);
    }

    @Test
    void optimizedProfileCutsQueriesPerPaymentInHalf() {
        UUID payer = openAccount("Opt Payer", "100000.00");
        UUID payee = openAccount("Opt Payee", "0.00");
        // Um pouco de historico para as regras terem o que ler.
        for (int i = 0; i < 12; i++) {
            pay(payer, payee, "15.00", "opt-device");
        }

        profile.activate(FraudProfile.HEAVY);
        long heavyQueries = queriesOf(pay(payer, payee, "20.00", "opt-device"));

        profile.activate(FraudProfile.HEAVY_OPTIMIZED);
        long optimizedQueries = queriesOf(pay(payer, payee, "20.00", "opt-device"));

        assertThat(optimizedQueries).isLessThan(heavyQueries / 2);
        assertThat(optimizedQueries).isLessThanOrEqualTo(20);
    }

    @Test
    void optimizedProfileKeepsTheSameDecisionAsHeavy() {
        UUID payer = openAccount("Opt Same", "100000.00");
        UUID payee = openAccount("Opt Same Payee", "0.00");
        pay(payer, payee, "10.00", "opt-device-same"); // estabelece a relacao: new-payee nao dispara mais

        profile.activate(FraudProfile.HEAVY);
        ResponseEntity<Map> heavy = pay(payer, payee, "5500.50", "opt-device-same");
        profile.activate(FraudProfile.HEAVY_OPTIMIZED);
        ResponseEntity<Map> optimized = pay(payer, payee, "5500.50", "opt-device-same");

        // amount-limit (40) + new-account (20) nos dois; nenhuma regra de historico dispara; mesma decisao, mesmo score.
        assertThat(heavy.getBody().get("fraudScore")).isEqualTo(60);
        assertThat(optimized.getBody().get("fraudScore")).isEqualTo(60);
        assertThat(optimized.getBody().get("status")).isEqualTo(heavy.getBody().get("status")).isEqualTo("APPROVED");
    }

    @Test
    void cachedBlacklistBlocksWithoutQueryingTheDatabase() {
        jdbc.sql("INSERT INTO fraud_blacklist (id, kind, value, reason, created_at) VALUES (:id, 'DEVICE', 'stolen-phone', 'test', :now)")
                .param("id", UUID.randomUUID()).param("now", Timestamp.from(Instant.now())).update();
        blacklist.refresh();
        profile.activate(FraudProfile.HEAVY_OPTIMIZED);
        UUID payer = openAccount("Opt Black", "100.00");
        UUID payee = openAccount("Opt Black Payee", "0.00");

        ResponseEntity<Map> response = pay(payer, payee, "10.00", "stolen-phone");

        assertThat(response.getBody().get("status")).isEqualTo("REJECTED");
        assertThat((Iterable<String>) response.getBody().get("fraudRules")).contains("blacklist-cached");
        assertThat(metrics.find("fraud.rule.queries").tag("rule", "blacklist-cached").summary().max()).isZero();
    }

    private static long queriesOf(ResponseEntity<Map> response) {
        return ((Number) response.getBody().get("dbQueries")).longValue();
    }
}
