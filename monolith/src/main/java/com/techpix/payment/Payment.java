package com.techpix.payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record Payment(
        UUID id,
        UUID payerAccountId,
        UUID payeeAccountId,
        BigDecimal amount,
        String deviceId,
        PaymentStatus status,
        String rejectionReason,
        Instant createdAt) {
}
