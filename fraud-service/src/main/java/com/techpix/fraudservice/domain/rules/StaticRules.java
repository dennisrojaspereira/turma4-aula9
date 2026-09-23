package com.techpix.fraudservice.domain.rules;

import com.techpix.fraudservice.domain.PaymentFacts;
import com.techpix.fraudservice.domain.RiskRule;
import java.math.BigDecimal;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Regras que só olham o pagamento em si. Sem histórico, sem banco, sem rede.
 * Mesmos limiares e pesos do monólito: o serviço precisa decidir igual para poder substituí-lo.
 */
public final class StaticRules {

    private StaticRules() {
    }

    public static List<RiskRule> all() {
        return List.of(new AmountLimit(), new SameParty(), new NightTime(), new RoundAmount());
    }

    static final class AmountLimit implements RiskRule {
        static final BigDecimal LIMIT = new BigDecimal("5000.00");

        public String name() {
            return "amount-limit";
        }

        public int evaluate(PaymentFacts f) {
            return f.amount().compareTo(LIMIT) > 0 ? 40 : 0;
        }
    }

    static final class SameParty implements RiskRule {
        public String name() {
            return "same-party";
        }

        public int evaluate(PaymentFacts f) {
            return f.payerAccountId().equals(f.payeeAccountId()) ? 100 : 0;
        }
    }

    static final class NightTime implements RiskRule {
        public String name() {
            return "night-time";
        }

        public int evaluate(PaymentFacts f) {
            return f.occurredAt().atZone(ZoneOffset.UTC).getHour() < 5 ? 15 : 0;
        }
    }

    static final class RoundAmount implements RiskRule {
        public String name() {
            return "round-amount";
        }

        public int evaluate(PaymentFacts f) {
            boolean round = f.amount().remainder(new BigDecimal("100.00")).signum() == 0;
            return round && f.amount().compareTo(new BigDecimal("1000.00")) >= 0 ? 10 : 0;
        }
    }
}
