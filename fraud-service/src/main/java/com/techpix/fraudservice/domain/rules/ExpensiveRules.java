package com.techpix.fraudservice.domain.rules;

import com.techpix.fraudservice.domain.PaymentFacts;
import com.techpix.fraudservice.domain.RiskRule;
import java.util.List;

/**
 * As duas regras que justificaram a extração: uma espera rede, a outra queima CPU.
 * Aqui elas consomem recursos do Fraud Service, não do Payment.
 */
public final class ExpensiveRules {

    private ExpensiveRules() {
    }

    public static List<RiskRule> all(long providerLatencyMs, int mlIterations) {
        return List.of(new ExternalProvider(providerLatencyMs), new MlScoring(mlIterations));
    }

    record ExternalProvider(long latencyMs) implements RiskRule {
        public String name() {
            return "external-provider";
        }

        public int evaluate(PaymentFacts f) {
            try {
                Thread.sleep(latencyMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return Math.floorMod(f.payeeAccountId().hashCode(), 100) < 5 ? 20 : 0;
        }
    }

    record MlScoring(int iterations) implements RiskRule {
        public String name() {
            return "ml-scoring";
        }

        public int evaluate(PaymentFacts f) {
            long h = f.payerAccountId().getMostSignificantBits() ^ f.payeeAccountId().getLeastSignificantBits()
                    ^ f.amount().unscaledValue().longValue();
            for (int i = 0; i < iterations; i++) {
                h = h * 6364136223846793005L + 1442695040888963407L;
                h ^= (h >>> 29);
            }
            return Math.floorMod(h, 1000) < 30 ? 30 : 0;
        }
    }
}
