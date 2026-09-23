package com.techpix.fraud.internal.rules;

import com.techpix.fraud.FraudCheck;
import com.techpix.fraud.internal.FraudRule;
import java.time.ZoneOffset;
import org.springframework.stereotype.Component;

/** Madrugada (UTC) concentra mais fraude. Peso baixo, apenas soma com outras regras. */
@Component
public class NightTimeRule implements FraudRule {

    @Override
    public String name() {
        return "night-time";
    }

    @Override
    public int evaluate(FraudCheck check) {
        int hour = check.occurredAt().atZone(ZoneOffset.UTC).getHour();
        return hour < 5 ? 15 : 0;
    }
}
