package com.techpix.fraud.rules.heavy;

import com.techpix.fraud.FraudCheck;
import com.techpix.fraud.FraudProfile;
import com.techpix.fraud.FraudProperties;
import com.techpix.fraud.FraudRule;
import java.util.EnumSet;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * "Modelo de ML" de fraude. Simulado com um cálculo determinístico que consome CPU de forma
 * proporcional a {@code techpix.fraud.ml-iterations}.
 * <p>
 * Não usa o banco. Mas usa a CPU do mesmo processo que atende Payment. Quando Fraud
 * precisa de mais CPU, Payment paga a conta.
 */
@Component
public class MlScoringRule implements FraudRule {

    private final FraudProperties properties;

    public MlScoringRule(FraudProperties properties) {
        this.properties = properties;
    }

    @Override
    public String name() {
        return "ml-scoring";
    }

    @Override
    public Set<FraudProfile> profiles() {
        return EnumSet.of(FraudProfile.HEAVY, FraudProfile.HEAVY_OPTIMIZED);
    }

    @Override
    public int evaluate(FraudCheck check) {
        long h = check.payerAccountId().getMostSignificantBits() ^ check.payeeAccountId().getLeastSignificantBits()
                ^ check.amount().unscaledValue().longValue();
        for (int i = 0; i < properties.mlIterations(); i++) {
            h = h * 6364136223846793005L + 1442695040888963407L;
            h ^= (h >>> 29);
        }
        int probability = Math.floorMod(h, 1000);
        // ~3% dos pagamentos recebem um score alto do modelo.
        return probability < 30 ? 30 : 0;
    }
}
