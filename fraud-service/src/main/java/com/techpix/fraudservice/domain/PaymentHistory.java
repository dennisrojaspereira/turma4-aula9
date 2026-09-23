package com.techpix.fraudservice.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Porta: o que o domínio precisa saber do passado. Quem implementa decide de onde os dados vêm.
 * <p>
 * Hoje: o schema legado do monólito, pela ACL. Amanhã: uma visão local alimentada por eventos.
 * As regras não mudam quando a fonte muda. Essa é a razão de a porta existir.
 */
public interface PaymentHistory {

    record PayerSnapshot(long lastMinute, long lastHour, long lastDay, BigDecimal lastWeekTotal,
                         long total, BigDecimal averageAmount, long sameHourCount, long rejectedLastDay) {
    }

    record PayeeSnapshot(long rejectedLast30Days, long distinctPayersLastHour) {
    }

    PayerSnapshot payerSnapshot(UUID payerAccountId, Instant at, UUID excludingPaymentId);

    PayeeSnapshot payeeSnapshot(UUID payeeAccountId, Instant at, UUID excludingPaymentId);

    long countBetween(UUID payerAccountId, UUID payeeAccountId, UUID excludingPaymentId);

    long distinctPayersByDeviceSince(String deviceId, Instant since, UUID excludingPaymentId);
}
