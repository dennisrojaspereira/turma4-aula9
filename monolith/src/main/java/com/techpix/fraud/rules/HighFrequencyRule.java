package com.techpix.fraud.rules;

import com.techpix.fraud.FraudCheck;
import com.techpix.fraud.FraudHistoryRepository;
import com.techpix.fraud.FraudRule;
import java.time.Duration;
import org.springframework.stereotype.Component;

/** Muitos pagamentos em um minuto. Uma consulta ao banco. */
@Component
public class HighFrequencyRule implements FraudRule {

    private final FraudHistoryRepository history;

    public HighFrequencyRule(FraudHistoryRepository history) {
        this.history = history;
    }

    @Override
    public String name() {
        return "high-frequency";
    }

    @Override
    public int evaluate(FraudCheck check) {
        long recent = history.countByPayerSince(check.payerAccountId(), check.occurredAt().minus(Duration.ofMinutes(1)), check.paymentId());
        return recent >= 5 ? 40 : 0;
    }
}
