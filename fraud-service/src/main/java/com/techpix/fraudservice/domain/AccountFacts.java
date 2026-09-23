package com.techpix.fraudservice.domain;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Porta: o pouco que o domínio precisa saber sobre contas. */
public interface AccountFacts {

    Optional<Instant> accountOpenedAt(UUID accountId);
}
