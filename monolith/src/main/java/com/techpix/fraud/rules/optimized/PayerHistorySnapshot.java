package com.techpix.fraud.rules.optimized;

import java.math.BigDecimal;

/**
 * Tudo que as regras de histórico do pagador precisam, calculado pelo banco em UMA consulta.
 * <p>
 * Substitui: 3 consultas de velocity, 2 carregamentos do histórico inteiro e o N+1 de rejeições.
 */
public record PayerHistorySnapshot(
        long lastMinute,
        long lastHour,
        long lastDay,
        BigDecimal lastWeekTotal,
        long total,
        BigDecimal averageAmount,
        long sameHourCount,
        long rejectedLastDay) {
}
