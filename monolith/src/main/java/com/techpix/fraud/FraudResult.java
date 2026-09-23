package com.techpix.fraud;

import java.util.List;

public record FraudResult(int score, FraudDecision decision, List<String> triggeredRules, int rulesEvaluated, long durationMs) {

    public boolean rejected() {
        return decision == FraudDecision.REJECTED;
    }
}
