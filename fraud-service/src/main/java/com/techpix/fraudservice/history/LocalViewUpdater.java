package com.techpix.fraudservice.history;

import com.techpix.fraudservice.domain.PaymentFacts;
import com.techpix.fraudservice.messaging.PaymentEventMessage;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Mantém a visão local de Fraud a partir de duas fontes:
 * <ul>
 *   <li>a própria avaliação (status EVALUATED): Fraud sabe que um pagamento está em andamento
 *       antes de qualquer evento chegar. Velocity precisa disso para não ser enganada por rajadas;</li>
 *   <li>os fatos de Payment (PaymentApproved / PaymentRejected), via Kafka.</li>
 * </ul>
 * Duplicação intencional: {@code payment_history} é uma cópia parcial de {@code payments}. Com dono.
 */
@Component
@ConditionalOnProperty(name = "fraud.history-source", havingValue = "LOCAL_VIEW")
public class LocalViewUpdater {

    private static final Logger log = LoggerFactory.getLogger(LocalViewUpdater.class);

    private final JdbcClient jdbc;
    private final ProcessedEvents processedEvents;
    private final Clock clock;
    private final Counter applied;
    private final Counter duplicates;

    public LocalViewUpdater(JdbcClient jdbc, ProcessedEvents processedEvents, Clock clock, MeterRegistry metrics) {
        this.jdbc = jdbc;
        this.processedEvents = processedEvents;
        this.clock = clock;
        this.applied = Counter.builder("fraud.events.applied").register(metrics);
        this.duplicates = Counter.builder("fraud.events.duplicates").description("Eventos ja processados, ignorados").register(metrics);
    }

    /** Chamado pela avaliação: o pagamento existe e está sendo decidido. */
    public void recordEvaluated(PaymentFacts facts) {
        jdbc.sql("""
                INSERT INTO payment_history (payment_id, payer_account_id, payee_account_id, amount, device_id, status, occurred_at, updated_at)
                VALUES (:id, :payer, :payee, :amount, :device, 'EVALUATED', :at, :now)
                ON CONFLICT (payment_id) DO NOTHING
                """)
                .param("id", facts.paymentId()).param("payer", facts.payerAccountId()).param("payee", facts.payeeAccountId())
                .param("amount", facts.amount()).param("device", facts.deviceId())
                .param("at", Timestamp.from(facts.occurredAt())).param("now", Timestamp.from(Instant.now(clock)))
                .update();
    }

    /**
     * Chamado pelo consumidor Kafka. Idempotente: o mesmo eventId muda o estado uma vez.
     * @return true se o evento foi aplicado; false se era duplicata
     */
    @Transactional
    public boolean apply(PaymentEventMessage event) {
        if (!processedEvents.markProcessed(event.eventId(), event.type())) {
            duplicates.increment();
            log.info("event {} payment={} eventId={} DUPLICADO, ignorado", event.type(), event.paymentId(), event.eventId());
            return false;
        }
        String status = event.isApproved() ? "APPROVED" : event.isRejected() ? "REJECTED" : "UNKNOWN";
        Timestamp now = Timestamp.from(Instant.now(clock));
        jdbc.sql("""
                INSERT INTO payment_history (payment_id, payer_account_id, payee_account_id, amount, device_id, status, payer_opened_at, occurred_at, updated_at)
                VALUES (:id, :payer, :payee, :amount, :device, :status, :opened, :at, :now)
                ON CONFLICT (payment_id) DO UPDATE SET status = EXCLUDED.status, payer_opened_at = EXCLUDED.payer_opened_at, updated_at = EXCLUDED.updated_at
                """)
                .param("id", event.paymentId()).param("payer", event.payerAccountId()).param("payee", event.payeeAccountId())
                .param("amount", event.amount()).param("device", event.deviceId()).param("status", status)
                .param("opened", event.payerAccountOpenedAt() == null ? null : Timestamp.from(event.payerAccountOpenedAt()))
                .param("at", Timestamp.from(event.occurredAt())).param("now", now)
                .update();

        // O agregado por INCREMENTO: nao e naturalmente idempotente. Sem processed_events, conta em dobro.
        jdbc.sql("""
                INSERT INTO account_activity (account_id, approved_count, approved_amount_total, rejected_count, updated_at)
                VALUES (:account, :approved, :amount, :rejected, :now)
                ON CONFLICT (account_id) DO UPDATE SET
                    approved_count = account_activity.approved_count + EXCLUDED.approved_count,
                    approved_amount_total = account_activity.approved_amount_total + EXCLUDED.approved_amount_total,
                    rejected_count = account_activity.rejected_count + EXCLUDED.rejected_count,
                    updated_at = EXCLUDED.updated_at
                """)
                .param("account", event.payerAccountId())
                .param("approved", event.isApproved() ? 1 : 0)
                .param("amount", event.isApproved() ? event.amount() : java.math.BigDecimal.ZERO)
                .param("rejected", event.isRejected() ? 1 : 0)
                .param("now", now)
                .update();
        applied.increment();
        log.info("event {} payment={} eventId={} aplicado: status={}", event.type(), event.paymentId(), event.eventId(), status);
        return true;
    }

    public Map<String, Object> activityOf(UUID accountId) {
        return jdbc.sql("SELECT approved_count, approved_amount_total, rejected_count FROM account_activity WHERE account_id = :id")
                .param("id", accountId)
                .query((rs, i) -> Map.<String, Object>of(
                        "accountId", accountId,
                        "approvedCount", rs.getLong("approved_count"),
                        "approvedAmountTotal", rs.getBigDecimal("approved_amount_total"),
                        "rejectedCount", rs.getLong("rejected_count")))
                .optional()
                .orElse(Map.of("accountId", accountId, "approvedCount", 0L, "approvedAmountTotal", java.math.BigDecimal.ZERO, "rejectedCount", 0L));
    }
}
