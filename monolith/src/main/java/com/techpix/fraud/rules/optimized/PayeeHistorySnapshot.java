package com.techpix.fraud.rules.optimized;

/** Reputação e velocity do destinatário em uma consulta. */
public record PayeeHistorySnapshot(long rejectedLast30Days, long distinctPayersLastHour) {
}
