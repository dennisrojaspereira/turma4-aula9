package com.techpix.fraud.rules.heavy;

import com.techpix.fraud.FraudCheck;
import com.techpix.fraud.FraudHistoryRepository;
import com.techpix.fraud.FraudProfile;
import com.techpix.fraud.FraudRule;
import java.time.Duration;
import java.util.EnumSet;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Conta criada há menos de 24 horas. Uma consulta barata por chave primária. */
@Component
public class NewAccountRule implements FraudRule {

    private final FraudHistoryRepository history;

    public NewAccountRule(FraudHistoryRepository history) {
        this.history = history;
    }

    @Override
    public String name() {
        return "new-account";
    }

    @Override
    public Set<FraudProfile> profiles() {
        return EnumSet.of(FraudProfile.HEAVY, FraudProfile.HEAVY_OPTIMIZED);
    }

    @Override
    public int evaluate(FraudCheck check) {
        return Duration.between(history.accountCreatedAt(check.payerAccountId()), check.occurredAt()).toHours() < 24 ? 20 : 0;
    }
}
