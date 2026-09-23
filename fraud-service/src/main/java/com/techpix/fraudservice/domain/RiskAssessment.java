package com.techpix.fraudservice.domain;

import java.util.List;

public record RiskAssessment(int score, Decision decision, List<String> triggeredRules, int rulesEvaluated) {

    public enum Decision {
        APPROVED, REJECTED
    }
}
