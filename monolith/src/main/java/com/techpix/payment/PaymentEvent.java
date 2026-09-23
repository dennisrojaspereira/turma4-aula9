package com.techpix.payment;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

/**
 * Um fato sobre um pagamento, contado por Payment para quem quiser ouvir.
 * <p>
 * Isto é o contrato público de Payment. Não é a linha da tabela (não tem {@code rejection_reason},
 * não tem saldo). É o que outros contextos precisam saber, e nada mais.
 * <p>
 * {@code eventId} é determinístico (pagamento + tipo): republicar o mesmo fato gera o mesmo id.
 * É isso que permite ao consumidor detectar duplicatas (lab 19) e a nós simular redelivery.
 */
public record PaymentEvent(
        UUID eventId,
        Type type,
        UUID paymentId,
        UUID payerAccountId,
        UUID payeeAccountId,
        BigDecimal amount,
        String deviceId,
        Instant payerAccountOpenedAt,
        Instant occurredAt) {

    public enum Type {
        PaymentApproved, PaymentRejected
    }

    public static PaymentEvent of(Type type, Payment payment, Instant payerAccountOpenedAt) {
        UUID eventId = UUID.nameUUIDFromBytes((payment.id() + ":" + type).getBytes(StandardCharsets.UTF_8));
        return new PaymentEvent(eventId, type, payment.id(), payment.payerAccountId(), payment.payeeAccountId(),
                payment.amount(), payment.deviceId(), payerAccountOpenedAt, payment.createdAt());
    }
}
