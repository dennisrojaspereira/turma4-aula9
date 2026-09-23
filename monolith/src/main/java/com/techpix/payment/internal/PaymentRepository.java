package com.techpix.payment.internal;

import com.techpix.payment.PaymentStatus;
import com.techpix.payment.Payment;
import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class PaymentRepository {

    private final JdbcClient jdbc;

    public PaymentRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(Payment payment) {
        jdbc.sql("""
                INSERT INTO payments (id, payer_account_id, payee_account_id, amount, device_id, status, rejection_reason, created_at)
                VALUES (:id, :payer, :payee, :amount, :device, :status, :reason, :createdAt)
                """)
                .param("id", payment.id())
                .param("payer", payment.payerAccountId())
                .param("payee", payment.payeeAccountId())
                .param("amount", payment.amount())
                .param("device", payment.deviceId())
                .param("status", payment.status().name())
                .param("reason", payment.rejectionReason())
                .param("createdAt", Timestamp.from(payment.createdAt()))
                .update();
    }

    public void updateStatus(UUID id, PaymentStatus status, String rejectionReason) {
        jdbc.sql("UPDATE payments SET status = :status, rejection_reason = :reason WHERE id = :id")
                .param("status", status.name())
                .param("reason", rejectionReason)
                .param("id", id)
                .update();
    }

    public Optional<Payment> findById(UUID id) {
        return jdbc.sql("SELECT * FROM payments WHERE id = :id")
                .param("id", id)
                .query((rs, i) -> new Payment(
                        rs.getObject("id", UUID.class),
                        rs.getObject("payer_account_id", UUID.class),
                        rs.getObject("payee_account_id", UUID.class),
                        rs.getBigDecimal("amount"),
                        rs.getString("device_id"),
                        PaymentStatus.valueOf(rs.getString("status")),
                        rs.getString("rejection_reason"),
                        rs.getTimestamp("created_at").toInstant()))
                .optional();
    }
}
