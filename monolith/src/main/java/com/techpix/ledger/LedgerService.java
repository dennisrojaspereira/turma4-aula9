package com.techpix.ledger;

import com.techpix.ledger.internal.LedgerRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Ledger de partidas dobradas: todo pagamento gera um DEBIT e um CREDIT na mesma transação. */
@Service
public class LedgerService {

    private final LedgerRepository repository;
    private final Clock clock;

    public LedgerService(LedgerRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public void record(UUID paymentId, UUID payerAccountId, UUID payeeAccountId, BigDecimal amount) {
        Instant now = Instant.now(clock);
        repository.insert(new LedgerEntry(UUID.randomUUID(), paymentId, payerAccountId, LedgerEntry.EntryType.DEBIT, amount, now));
        repository.insert(new LedgerEntry(UUID.randomUUID(), paymentId, payeeAccountId, LedgerEntry.EntryType.CREDIT, amount, now));
    }

    public List<LedgerEntry> entriesOf(UUID paymentId) {
        return repository.findByPayment(paymentId);
    }
}
