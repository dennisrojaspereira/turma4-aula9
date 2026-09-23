package com.techpix.fraudservice.messaging;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * O contrato publicado por Payment, como o Fraud Service o lê.
 * <p>
 * É uma classe separada do {@code PaymentEvent} do monólito de propósito: não compartilhamos código
 * entre os dois projetos. Se Payment adicionar um campo, ignoramos. Se remover um que usamos, o teste
 * de contrato quebra aqui, e não em produção. Campos desconhecidos são ignorados (ver application.yml).
 */
public record PaymentEventMessage(
        UUID eventId,
        String type,
        UUID paymentId,
        UUID payerAccountId,
        UUID payeeAccountId,
        BigDecimal amount,
        String deviceId,
        Instant payerAccountOpenedAt,
        Instant occurredAt) {

    public boolean isApproved() {
        return "PaymentApproved".equals(type);
    }

    public boolean isRejected() {
        return "PaymentRejected".equals(type);
    }
}
