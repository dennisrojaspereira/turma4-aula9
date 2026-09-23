package com.techpix.fraud.internal.rules.heavy;

import com.techpix.fraud.FraudCheck;
import com.techpix.fraud.FraudProfile;
import com.techpix.fraud.internal.FraudRule;
import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Valores redondos e altos são mais comuns em fraude do que em compras reais. Sem consulta. */
@Component
public class RoundAmountRule implements FraudRule {

    @Override
    public String name() {
        return "round-amount";
    }

    @Override
    public Set<FraudProfile> profiles() {
        return EnumSet.of(FraudProfile.HEAVY, FraudProfile.HEAVY_OPTIMIZED);
    }

    @Override
    public int evaluate(FraudCheck check) {
        boolean round = check.amount().remainder(new BigDecimal("100.00")).signum() == 0;
        return round && check.amount().compareTo(new BigDecimal("1000.00")) >= 0 ? 10 : 0;
    }
}
