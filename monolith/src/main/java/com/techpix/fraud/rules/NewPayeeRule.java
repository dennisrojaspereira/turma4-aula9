package com.techpix.fraud.rules;

import com.techpix.fraud.FraudCheck;
import com.techpix.fraud.FraudHistoryRepository;
import com.techpix.fraud.FraudRule;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

/** Primeiro pagamento para um destinatário, com valor relevante. Uma consulta ao banco. */
@Component
public class NewPayeeRule implements FraudRule {

    static final BigDecimal RELEVANT_AMOUNT = new BigDecimal("1000.00");

    private final FraudHistoryRepository history;

    public NewPayeeRule(FraudHistoryRepository history) {
        this.history = history;
    }

    @Override
    public String name() {
        return "new-payee";
    }

    @Override
    public int evaluate(FraudCheck check) {
        if (check.amount().compareTo(RELEVANT_AMOUNT) < 0) {
            return 0;
        }
        long previous = history.countBetween(check.payerAccountId(), check.payeeAccountId(), check.paymentId());
        return previous == 0 ? 25 : 0;
    }
}
