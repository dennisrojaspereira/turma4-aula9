package com.techpix.ledger;

import com.techpix.ledger.internal.LedgerRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Ledger de partidas dobradas: todo pagamento gera um DEBIT e um CREDIT na mesma transação. */
@Service
public class LedgerService {

    private final LedgerRepository repository;
    private final Clock clock;
    private final BigDecimal failAmount;

    public LedgerService(LedgerRepository repository, Clock clock,
                         @Value("${techpix.ledger.fail-amount:}") String failAmount) {
        this.repository = repository;
        this.clock = clock;
        this.failAmount = failAmount == null || failAmount.isBlank() ? null : new BigDecimal(failAmount);
    }

    /** Erro de infraestrutura do ledger. Simulado no laboratorio por um valor "magico" (techpix.ledger.fail-amount). */
    public static class LedgerUnavailableException extends RuntimeException {
        public LedgerUnavailableException(String message) {
            super(message);
        }
    }

    @Transactional
    public void record(UUID paymentId, UUID payerAccountId, UUID payeeAccountId, BigDecimal amount) {
        if (failAmount != null && failAmount.compareTo(amount) == 0) {
            // Lab 20: simula o ledger fora do ar DEPOIS de Fraud ja ter decidido e registrado o pagamento.
            throw new LedgerUnavailableException("ledger unavailable (simulated for amount " + amount + ")");
        }
        Instant now = Instant.now(clock);
        repository.insert(new LedgerEntry(UUID.randomUUID(), paymentId, payerAccountId, LedgerEntry.EntryType.DEBIT, amount, now));
        repository.insert(new LedgerEntry(UUID.randomUUID(), paymentId, payeeAccountId, LedgerEntry.EntryType.CREDIT, amount, now));
    }

    public List<LedgerEntry> entriesOf(UUID paymentId) {
        return repository.findByPayment(paymentId);
    }
}
