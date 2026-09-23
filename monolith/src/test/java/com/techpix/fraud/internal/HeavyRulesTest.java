package com.techpix.fraud.internal;

import com.techpix.fraud.FraudMode;
import com.techpix.fraud.FraudProfile;
import com.techpix.fraud.FraudCheck;
import static org.assertj.core.api.Assertions.assertThat;

import com.techpix.fraud.internal.rules.heavy.MlScoringRule;
import com.techpix.fraud.internal.rules.heavy.RoundAmountRule;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class HeavyRulesTest {

    private static FraudCheck check(String amount) {
        return new FraudCheck(UUID.randomUUID(),
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                UUID.fromString("00000000-0000-0000-0000-000000000002"),
                new BigDecimal(amount), "d", Instant.parse("2026-01-01T12:00:00Z"));
    }

    @Test
    void roundHighAmountsScore10() {
        RoundAmountRule rule = new RoundAmountRule();
        assertThat(rule.evaluate(check("1000.00"))).isEqualTo(10);
        assertThat(rule.evaluate(check("1050.00"))).isZero();
        assertThat(rule.evaluate(check("100.00"))).isZero();
    }

    @Test
    void mlScoringIsDeterministicForSameInput() {
        MlScoringRule rule = new MlScoringRule(props(10_000));
        FraudCheck check = check("123.45");
        assertThat(rule.evaluate(check)).isEqualTo(rule.evaluate(check));
    }

    @Test
    void mlScoringCostGrowsWithIterations() {
        FraudCheck check = check("123.45");
        long cheap = timeOf(new MlScoringRule(props(1_000)), check);
        long expensive = timeOf(new MlScoringRule(props(5_000_000)), check);
        assertThat(expensive).isGreaterThan(cheap);
    }

    private static FraudProperties props(int mlIterations) {
        return new FraudProperties(70, FraudProfile.HEAVY, FraudMode.LEGACY, 0, mlIterations,
                new FraudProperties.Remote("http://localhost:8081", 500, 2000), new FraudProperties.Parallel(1500, 2, 10), new FraudProperties.Canary(0));
    }

    private static long timeOf(MlScoringRule rule, FraudCheck check) {
        long start = System.nanoTime();
        rule.evaluate(check);
        return System.nanoTime() - start;
    }
}
