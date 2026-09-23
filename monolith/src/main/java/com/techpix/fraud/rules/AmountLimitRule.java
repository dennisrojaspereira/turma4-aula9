package com.techpix.fraud.rules;

import com.techpix.fraud.FraudCheck;
import com.techpix.fraud.FraudRule;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

/** Valores altos merecem atenção. Nenhuma consulta ao banco. */
@Component
public class AmountLimitRule implements FraudRule {

    static final BigDecimal LIMIT = new BigDecimal("5000.00");

    @Override
    public String name() {
        return "amount-limit";
    }

    @Override
    public int evaluate(FraudCheck check) {
        return check.amount().compareTo(LIMIT) > 0 ? 40 : 0;
    }
}
