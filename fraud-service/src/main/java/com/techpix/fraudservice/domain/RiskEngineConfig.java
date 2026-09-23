package com.techpix.fraudservice.domain;

import com.techpix.fraudservice.config.FraudServiceProperties;
import com.techpix.fraudservice.domain.rules.ExpensiveRules;
import com.techpix.fraudservice.domain.rules.HistoryRules;
import com.techpix.fraudservice.domain.rules.StaticRules;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.ArrayList;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Monta o motor de risco a partir das portas. A única classe do domínio que conhece o Spring. */
@Configuration
public class RiskEngineConfig {

    @Bean
    RiskEngine riskEngine(FraudServiceProperties props, PaymentHistory history, AccountFacts accounts,
                          Blacklist blacklist, MeterRegistry metrics) {
        List<RiskRule> rules = new ArrayList<>();
        rules.addAll(StaticRules.all());
        rules.addAll(HistoryRules.all(history, accounts, blacklist));
        rules.addAll(ExpensiveRules.all(props.providerLatencyMs(), props.mlIterations()));
        return new RiskEngine(rules, props.rejectThreshold(), metrics);
    }
}
