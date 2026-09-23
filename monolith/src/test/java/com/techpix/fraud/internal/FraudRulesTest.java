package com.techpix.fraud.internal;

import com.techpix.fraud.FraudCheck;
import static org.assertj.core.api.Assertions.assertThat;

import com.techpix.fraud.internal.rules.AmountLimitRule;
import com.techpix.fraud.internal.rules.NightTimeRule;
import com.techpix.fraud.internal.rules.SamePartyRule;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Regras puras, sem banco. Testes unitários de verdade. */
class FraudRulesTest {

    private static final UUID PAYER = UUID.randomUUID();
    private static final UUID PAYEE = UUID.randomUUID();

    private static FraudCheck check(UUID payee, String amount, String at) {
        return new FraudCheck(UUID.randomUUID(), PAYER, payee, new BigDecimal(amount), "device-1", Instant.parse(at));
    }

    @Test
    void amountAboveLimitScores40() {
        AmountLimitRule rule = new AmountLimitRule();
        assertThat(rule.evaluate(check(PAYEE, "5000.01", "2026-01-01T12:00:00Z"))).isEqualTo(40);
        assertThat(rule.evaluate(check(PAYEE, "5000.00", "2026-01-01T12:00:00Z"))).isZero();
    }

    @Test
    void payingYourselfIsRejectedOutright() {
        SamePartyRule rule = new SamePartyRule();
        assertThat(rule.evaluate(check(PAYER, "10.00", "2026-01-01T12:00:00Z"))).isEqualTo(100);
        assertThat(rule.evaluate(check(PAYEE, "10.00", "2026-01-01T12:00:00Z"))).isZero();
    }

    @Test
    void nightTimeAddsSmallRisk() {
        NightTimeRule rule = new NightTimeRule();
        assertThat(rule.evaluate(check(PAYEE, "10.00", "2026-01-01T03:30:00Z"))).isEqualTo(15);
        assertThat(rule.evaluate(check(PAYEE, "10.00", "2026-01-01T09:00:00Z"))).isZero();
    }
}
