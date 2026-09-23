package com.techpix.fraud.internal;

import com.techpix.fraud.FraudResult;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class FraudEvaluationRepository {

    private final JdbcClient jdbc;

    public FraudEvaluationRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(UUID paymentId, FraudResult result, Instant evaluatedAt) {
        jdbc.sql("""
                INSERT INTO fraud_evaluations (id, payment_id, score, decision, triggered_rules, duration_ms, evaluated_at)
                VALUES (:id, :paymentId, :score, :decision, :rules, :duration, :evaluatedAt)
                """)
                .param("id", UUID.randomUUID())
                .param("paymentId", paymentId)
                .param("score", result.score())
                .param("decision", result.decision().name())
                .param("rules", String.join(",", result.triggeredRules()))
                .param("duration", result.durationMs())
                .param("evaluatedAt", Timestamp.from(evaluatedAt))
                .update();
    }

    public Optional<Integer> scoreOf(UUID paymentId) {
        return jdbc.sql("SELECT score FROM fraud_evaluations WHERE payment_id = :paymentId")
                .param("paymentId", paymentId)
                .query(Integer.class)
                .optional();
    }
}
