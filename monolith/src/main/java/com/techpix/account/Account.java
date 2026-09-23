package com.techpix.account;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record Account(UUID id, String ownerName, BigDecimal balance, AccountStatus status, Instant createdAt) {
}
