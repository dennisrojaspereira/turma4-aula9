package com.techpix.fraud.internal.rules.optimized;

/** Reputação e velocity do destinatário em uma consulta. */
public record PayeeHistorySnapshot(long rejectedLast30Days, long distinctPayersLastHour) {
}
