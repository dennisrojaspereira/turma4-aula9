package com.techpix.fraud.rules.heavy;

import com.techpix.fraud.FraudCheck;
import com.techpix.fraud.FraudHistoryRepository;
import com.techpix.fraud.FraudProfile;
import com.techpix.fraud.FraudRule;
import java.util.EnumSet;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Conta ou dispositivo em blacklist. Três consultas por pagamento em uma tabela sem índice. */
@Component
public class BlacklistRule implements FraudRule {

    private final FraudHistoryRepository history;

    public BlacklistRule(FraudHistoryRepository history) {
        this.history = history;
    }

    @Override
    public String name() {
        return "blacklist";
    }

    @Override
    public Set<FraudProfile> profiles() {
        return EnumSet.of(FraudProfile.HEAVY);
    }

    @Override
    public int evaluate(FraudCheck check) {
        if (history.isBlacklisted("ACCOUNT", check.payerAccountId().toString())
                || history.isBlacklisted("ACCOUNT", check.payeeAccountId().toString())
                || (check.deviceId() != null && history.isBlacklisted("DEVICE", check.deviceId()))) {
            return 100;
        }
        return 0;
    }
}
