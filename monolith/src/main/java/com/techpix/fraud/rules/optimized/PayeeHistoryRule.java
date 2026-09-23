package com.techpix.fraud.rules.optimized;

import com.techpix.fraud.FraudCheck;
import com.techpix.fraud.FraudProfile;
import com.techpix.fraud.FraudRule;
import java.util.EnumSet;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Uma consulta, duas regras: payee-reputation e payee-velocity. */
@Component
public class PayeeHistoryRule implements FraudRule {

    private final FraudHistoryAggregateRepository history;

    public PayeeHistoryRule(FraudHistoryAggregateRepository history) {
        this.history = history;
    }

    @Override
    public String name() {
        return "payee-history";
    }

    @Override
    public Set<FraudProfile> profiles() {
        return EnumSet.of(FraudProfile.HEAVY_OPTIMIZED);
    }

    @Override
    public int evaluate(FraudCheck check) {
        PayeeHistorySnapshot s = history.payeeSnapshot(check.payeeAccountId(), check.occurredAt(), check.paymentId());
        int points = 0;
        if (s.rejectedLast30Days() > 5) {
            points += 30;
        }
        if (s.distinctPayersLastHour() > 20) {
            points += 20;
        }
        return points;
    }
}
