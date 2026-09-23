package com.techpix.fraud;

import static org.assertj.core.api.Assertions.assertThat;

import com.techpix.support.AbstractIntegrationTest;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;

/**
 * O mesmo pagamento avaliado pelos dois perfis. O resultado de negócio é parecido,
 * o custo não é. Este teste é a primeira evidência da aula: Fraud cresceu.
 */
class FraudProfileIT extends AbstractIntegrationTest {

    @Autowired
    FraudProfileConfig profile;

    @AfterEach
    void restore() {
        profile.activate(FraudProfile.SIMPLE);
    }

    @Test
    void simpleProfileRunsFiveCheapRules() {
        profile.activate(FraudProfile.SIMPLE);
        UUID payer = openAccount("Simple Payer", "10000.00");
        UUID payee = openAccount("Simple Payee", "0.00");

        ResponseEntity<Map> response = pay(payer, payee, "50.00", "device-simple");

        assertThat(response.getBody().get("fraudRulesEvaluated")).isEqualTo(5);
        assertThat(response.getBody().get("status")).isEqualTo("APPROVED");
    }

    @Test
    void heavyProfileRunsSeventeenRulesIncludingHistoryProviderAndMl() {
        profile.activate(FraudProfile.HEAVY);
        UUID payer = openAccount("Heavy Payer", "10000.00");
        UUID payee = openAccount("Heavy Payee", "0.00");

        ResponseEntity<Map> response = pay(payer, payee, "50.00", "device-heavy");

        assertThat(response.getBody().get("fraudRulesEvaluated")).isEqualTo(17);
        // O provider externo simulado sozinho custa 30 ms. Fraud ficou visivelmente mais caro.
        assertThat(((Number) response.getBody().get("fraudDurationMs")).longValue()).isGreaterThanOrEqualTo(30L);
    }

    @Test
    void profileCanBeSwitchedAtRuntimeThroughAdminEndpoint() {
        http.put("/admin/fraud/profile", Map.of("profile", "HEAVY"));
        Map current = http.getForObject("/admin/fraud/profile", Map.class);
        assertThat(current.get("profile")).isEqualTo("HEAVY");
        assertThat(current.get("ruleCount")).isEqualTo(17);
    }
}
