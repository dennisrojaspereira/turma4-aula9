package com.techpix.fraudservice.persistence;

import com.techpix.fraudservice.domain.RiskAssessment;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Grava cada avaliação. A tabela {@code fraud_evaluations} pertence a Fraud, mesmo estando hoje no banco
 * compartilhado. A coluna {@code duration_ms} existe porque o monólito a criou; nós a preenchemos.
 */
@Repository
public class EvaluationStore {

    private final JdbcClient jdbc;

    public EvaluationStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void record(UUID paymentId, RiskAssessment assessment, long durationMs, Instant at) {
        jdbc.sql("""
                INSERT INTO fraud_evaluations (id, payment_id, score, decision, triggered_rules, duration_ms, evaluated_at)
                VALUES (:id, :paymentId, :score, :decision, :rules, :duration, :at)
                """)
                .param("id", UUID.randomUUID())
                .param("paymentId", paymentId)
                .param("score", assessment.score())
                .param("decision", assessment.decision().name())
                .param("rules", String.join(",", assessment.triggeredRules()))
                .param("duration", durationMs)
                .param("at", Timestamp.from(at))
                .update();
    }
}
