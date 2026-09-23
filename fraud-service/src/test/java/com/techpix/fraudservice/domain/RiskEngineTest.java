package com.techpix.fraudservice.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.techpix.fraudservice.domain.rules.HistoryRules;
import com.techpix.fraudservice.domain.rules.StaticRules;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * O domínio sem Spring, sem banco, sem HTTP. As portas são implementadas inline.
 * Isso só é possível porque as regras dependem de interfaces, não de tabelas.
 */
class RiskEngineTest {

    static final UUID PAYER = UUID.randomUUID();
    static final UUID PAYEE = UUID.randomUUID();
    static final Instant NOON = Instant.parse("2026-03-01T12:00:00Z");

    static PaymentHistory quietHistory() {
        return new PaymentHistory() {
            public PayerSnapshot payerSnapshot(UUID p, Instant at, UUID self) {
                return new PayerSnapshot(0, 0, 0, BigDecimal.ZERO, 100, new BigDecimal("50.00"), 10, 0);
            }

            public PayeeSnapshot payeeSnapshot(UUID p, Instant at, UUID self) {
                return new PayeeSnapshot(0, 0);
            }

            public long countBetween(UUID a, UUID b, UUID self) {
                return 3;
            }

            public long distinctPayersByDeviceSince(String d, Instant s, UUID self) {
                return 1;
            }
        };
    }

    static AccountFacts oldAccounts() {
        return id -> Optional.of(NOON.minusSeconds(86_400 * 365));
    }

    static Blacklist emptyBlacklist() {
        return new Blacklist() {
            public boolean containsAccount(String a) {
                return false;
            }

            public boolean containsDevice(String d) {
                return d.equals("stolen");
            }
        };
    }

    static RiskEngine engine(PaymentHistory history) {
        List<RiskRule> rules = new ArrayList<>(StaticRules.all());
        rules.addAll(HistoryRules.all(history, oldAccounts(), emptyBlacklist()));
        return new RiskEngine(rules, 70, new SimpleMeterRegistry());
    }

    static PaymentFacts facts(UUID payee, String amount, String device) {
        return new PaymentFacts(UUID.randomUUID(), PAYER, payee, new BigDecimal(amount), device, NOON);
    }

    @Test
    void ordinaryPaymentIsApprovedWithZeroScore() {
        RiskAssessment a = engine(quietHistory()).assess(facts(PAYEE, "42.00", "phone"));
        assertThat(a.score()).isZero();
        assertThat(a.decision()).isEqualTo(RiskAssessment.Decision.APPROVED);
        assertThat(a.rulesEvaluated()).isEqualTo(11);
    }

    @Test
    void scoresAccumulateAcrossRules() {
        // amount-limit (40) + round-amount (10) + payer-history: 6000 > 5x a media de 50 (25) = 75 -> REJECTED
        RiskAssessment a = engine(quietHistory()).assess(facts(PAYEE, "6000.00", "phone"));
        assertThat(a.score()).isEqualTo(75);
        assertThat(a.decision()).isEqualTo(RiskAssessment.Decision.REJECTED);
        assertThat(a.triggeredRules()).containsExactlyInAnyOrder("amount-limit", "round-amount", "payer-history");
    }

    @Test
    void blacklistedDeviceIsRejectedOutright() {
        RiskAssessment a = engine(quietHistory()).assess(facts(PAYEE, "5.00", "stolen"));
        assertThat(a.score()).isEqualTo(100);
        assertThat(a.decision()).isEqualTo(RiskAssessment.Decision.REJECTED);
    }

    @Test
    void payerHistoryCombinesVelocityAverageAndRejections() {
        PaymentHistory busy = new PaymentHistory() {
            public PayerSnapshot payerSnapshot(UUID p, Instant at, UUID self) {
                // 12 na ultima hora (+20), 2 rejeicoes hoje (+30), media 10 e pagamento 100 (+25)
                return new PayerSnapshot(0, 12, 12, new BigDecimal("500.00"), 200, new BigDecimal("10.00"), 20, 2);
            }

            public PayeeSnapshot payeeSnapshot(UUID p, Instant at, UUID self) {
                return new PayeeSnapshot(0, 0);
            }

            public long countBetween(UUID a, UUID b, UUID self) {
                return 1;
            }

            public long distinctPayersByDeviceSince(String d, Instant s, UUID self) {
                return 1;
            }
        };
        RiskAssessment a = engine(busy).assess(facts(PAYEE, "100.00", "phone"));
        assertThat(a.score()).isEqualTo(75);
        assertThat(a.decision()).isEqualTo(RiskAssessment.Decision.REJECTED);
        assertThat(a.triggeredRules()).containsExactly("payer-history");
    }
}
