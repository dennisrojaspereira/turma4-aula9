package com.techpix.fraudservice.domain.rules;

import com.techpix.fraudservice.domain.AccountFacts;
import com.techpix.fraudservice.domain.Blacklist;
import com.techpix.fraudservice.domain.PaymentFacts;
import com.techpix.fraudservice.domain.PaymentHistory;
import com.techpix.fraudservice.domain.PaymentHistory.PayeeSnapshot;
import com.techpix.fraudservice.domain.PaymentHistory.PayerSnapshot;
import com.techpix.fraudservice.domain.RiskRule;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

/**
 * Regras que precisam do passado. Dependem das portas, nunca de tabelas.
 * Equivalentes às do perfil HEAVY_OPTIMIZED do monólito.
 */
public final class HistoryRules {

    private HistoryRules() {
    }

    public static List<RiskRule> all(PaymentHistory history, AccountFacts accounts, Blacklist blacklist) {
        return List.of(
                new HighFrequency(history),
                new NewPayee(history),
                new PayerHistory(history),
                new PayeeHistory(history),
                new DeviceFingerprint(history),
                new NewAccount(accounts),
                new Blacklisted(blacklist));
    }

    record HighFrequency(PaymentHistory history) implements RiskRule {
        public String name() {
            return "high-frequency";
        }

        public int evaluate(PaymentFacts f) {
            return history.payerSnapshot(f.payerAccountId(), f.occurredAt(), f.paymentId()).lastMinute() >= 5 ? 40 : 0;
        }
    }

    record NewPayee(PaymentHistory history) implements RiskRule {
        static final BigDecimal RELEVANT = new BigDecimal("1000.00");

        public String name() {
            return "new-payee";
        }

        public int evaluate(PaymentFacts f) {
            if (f.amount().compareTo(RELEVANT) < 0) {
                return 0;
            }
            return history.countBetween(f.payerAccountId(), f.payeeAccountId(), f.paymentId()) == 0 ? 25 : 0;
        }
    }

    record PayerHistory(PaymentHistory history) implements RiskRule {
        public String name() {
            return "payer-history";
        }

        public int evaluate(PaymentFacts f) {
            PayerSnapshot s = history.payerSnapshot(f.payerAccountId(), f.occurredAt(), f.paymentId());
            int points = 0;
            if (s.lastHour() > 10) {
                points += 20;
            }
            if (s.lastDay() > 50) {
                points += 20;
            }
            if (s.lastWeekTotal().compareTo(new BigDecimal("20000.00")) > 0) {
                points += 20;
            }
            if (s.total() >= 5 && f.amount().compareTo(s.averageAmount().multiply(BigDecimal.valueOf(5))) > 0) {
                points += 25;
            }
            if (s.total() >= 50 && (double) s.sameHourCount() / s.total() < 0.02) {
                points += 15;
            }
            if (s.rejectedLastDay() >= 2) {
                points += 30;
            }
            return points;
        }
    }

    record PayeeHistory(PaymentHistory history) implements RiskRule {
        public String name() {
            return "payee-history";
        }

        public int evaluate(PaymentFacts f) {
            PayeeSnapshot s = history.payeeSnapshot(f.payeeAccountId(), f.occurredAt(), f.paymentId());
            return (s.rejectedLast30Days() > 5 ? 30 : 0) + (s.distinctPayersLastHour() > 20 ? 20 : 0);
        }
    }

    record DeviceFingerprint(PaymentHistory history) implements RiskRule {
        public String name() {
            return "device-fingerprint";
        }

        public int evaluate(PaymentFacts f) {
            if (f.deviceId() == null) {
                return 10;
            }
            return history.distinctPayersByDeviceSince(f.deviceId(), f.occurredAt().minus(Duration.ofDays(30)), f.paymentId()) > 3 ? 35 : 0;
        }
    }

    record NewAccount(AccountFacts accounts) implements RiskRule {
        public String name() {
            return "new-account";
        }

        public int evaluate(PaymentFacts f) {
            return accounts.accountOpenedAt(f.payerAccountId())
                    .map(opened -> Duration.between(opened, f.occurredAt()).toHours() < 24 ? 20 : 0)
                    .orElse(20);
        }
    }

    record Blacklisted(Blacklist blacklist) implements RiskRule {
        public String name() {
            return "blacklist";
        }

        public int evaluate(PaymentFacts f) {
            if (blacklist.containsAccount(f.payerAccountId().toString())
                    || blacklist.containsAccount(f.payeeAccountId().toString())
                    || (f.deviceId() != null && blacklist.containsDevice(f.deviceId()))) {
                return 100;
            }
            return 0;
        }
    }
}
