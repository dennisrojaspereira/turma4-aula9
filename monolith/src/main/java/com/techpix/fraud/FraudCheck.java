package com.techpix.fraud;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Tudo que Fraud precisa saber sobre um pagamento para decidir. */
public record FraudCheck(
        UUID paymentId,
        UUID payerAccountId,
        UUID payeeAccountId,
        BigDecimal amount,
        String deviceId,
        Instant occurredAt) {
}
