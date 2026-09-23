package com.techpix.ledger;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record LedgerEntry(UUID id, UUID paymentId, UUID accountId, EntryType type, BigDecimal amount, Instant createdAt) {

    public enum EntryType {
        DEBIT, CREDIT
    }
}
