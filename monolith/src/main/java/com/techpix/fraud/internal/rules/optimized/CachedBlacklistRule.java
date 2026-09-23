package com.techpix.fraud.internal.rules.optimized;

import com.techpix.fraud.FraudCheck;
import com.techpix.fraud.FraudProfile;
import com.techpix.fraud.internal.FraudRule;
import java.util.EnumSet;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Mesma regra de blacklist, zero consultas por pagamento. */
@Component
public class CachedBlacklistRule implements FraudRule {

    private final BlacklistCache cache;

    public CachedBlacklistRule(BlacklistCache cache) {
        this.cache = cache;
    }

    @Override
    public String name() {
        return "blacklist-cached";
    }

    @Override
    public Set<FraudProfile> profiles() {
        return EnumSet.of(FraudProfile.HEAVY_OPTIMIZED);
    }

    @Override
    public int evaluate(FraudCheck check) {
        if (cache.contains("ACCOUNT", check.payerAccountId().toString())
                || cache.contains("ACCOUNT", check.payeeAccountId().toString())
                || (check.deviceId() != null && cache.contains("DEVICE", check.deviceId()))) {
            return 100;
        }
        return 0;
    }
}
