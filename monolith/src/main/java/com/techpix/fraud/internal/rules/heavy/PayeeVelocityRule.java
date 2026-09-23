package com.techpix.fraud.internal.rules.heavy;

import com.techpix.fraud.FraudCheck;
import com.techpix.fraud.internal.FraudHistoryRepository;
import com.techpix.fraud.FraudProfile;
import com.techpix.fraud.internal.FraudRule;
import java.time.Duration;
import java.util.EnumSet;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Muitos pagadores distintos para o mesmo destinatário na última hora (mula financeira). */
@Component
public class PayeeVelocityRule implements FraudRule {

    private final FraudHistoryRepository history;

    public PayeeVelocityRule(FraudHistoryRepository history) {
        this.history = history;
    }

    @Override
    public String name() {
        return "payee-velocity";
    }

    @Override
    public Set<FraudProfile> profiles() {
        return EnumSet.of(FraudProfile.HEAVY);
    }

    @Override
    public int evaluate(FraudCheck check) {
        long payers = history.countDistinctPayersToPayeeSince(check.payeeAccountId(), check.occurredAt().minus(Duration.ofHours(1)), check.paymentId());
        return payers > 20 ? 20 : 0;
    }
}
