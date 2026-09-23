package com.techpix.fraudservice.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Os fatos sobre um pagamento que o domínio de risco precisa conhecer.
 * <p>
 * Este é o vocabulário do Fraud Service. Não é o {@code Payment} do monólito, nem a linha da tabela.
 * Quem traduz de fora para cá é a API (requisição HTTP) ou a ACL (schema legado).
 */
public record PaymentFacts(
        UUID paymentId,
        UUID payerAccountId,
        UUID payeeAccountId,
        BigDecimal amount,
        String deviceId,
        Instant occurredAt) {
}
