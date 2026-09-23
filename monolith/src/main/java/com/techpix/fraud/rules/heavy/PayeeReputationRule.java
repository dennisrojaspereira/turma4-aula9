package com.techpix.fraud.rules.heavy;

import com.techpix.fraud.FraudCheck;
import com.techpix.fraud.FraudHistoryRepository;
import com.techpix.fraud.FraudProfile;
import com.techpix.fraud.FraudRule;
import java.time.Duration;
import java.util.EnumSet;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Destinatário que recebeu muitos pagamentos rejeitados recentemente. Uma consulta, sem índice em payee. */
@Component
public class PayeeReputationRule implements FraudRule {

    private final FraudHistoryRepository history;

    public PayeeReputationRule(FraudHistoryRepository history) {
        this.history = history;
    }

    @Override
    public String name() {
        return "payee-reputation";
    }

    @Override
    public Set<FraudProfile> profiles() {
        return EnumSet.of(FraudProfile.HEAVY);
    }

    @Override
    public int evaluate(FraudCheck check) {
        long rejected = history.countRejectedToPayeeSince(check.payeeAccountId(), check.occurredAt().minus(Duration.ofDays(30)));
        return rejected > 5 ? 30 : 0;
    }
}
