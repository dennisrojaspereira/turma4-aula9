package com.techpix.fraud.rules.heavy;

import com.techpix.fraud.FraudCheck;
import com.techpix.fraud.FraudHistoryRepository;
import com.techpix.fraud.FraudProfile;
import com.techpix.fraud.FraudRule;
import java.time.Duration;
import java.util.EnumSet;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Um dispositivo usado por muitas contas diferentes. Uma consulta, sem índice em device_id. */
@Component
public class DeviceFingerprintRule implements FraudRule {

    private final FraudHistoryRepository history;

    public DeviceFingerprintRule(FraudHistoryRepository history) {
        this.history = history;
    }

    @Override
    public String name() {
        return "device-fingerprint";
    }

    @Override
    public Set<FraudProfile> profiles() {
        return EnumSet.of(FraudProfile.HEAVY);
    }

    @Override
    public int evaluate(FraudCheck check) {
        if (check.deviceId() == null) {
            return 10;
        }
        long accounts = history.countDistinctPayersByDeviceSince(check.deviceId(), check.occurredAt().minus(Duration.ofDays(30)), check.paymentId());
        return accounts > 3 ? 35 : 0;
    }
}
