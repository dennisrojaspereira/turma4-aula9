package com.techpix.account;

import com.techpix.account.internal.AccountRepository;
import com.techpix.shared.DomainException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {

    private final AccountRepository repository;
    private final Clock clock;

    public AccountService(AccountRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public Account open(String ownerName, BigDecimal initialBalance) {
        Account account = new Account(UUID.randomUUID(), ownerName, initialBalance, AccountStatus.ACTIVE, Instant.now(clock));
        repository.insert(account);
        return account;
    }

    public Account get(UUID id) {
        return repository.findById(id).orElseThrow(() -> DomainException.notFound("account " + id + " not found"));
    }

    public Account requireActive(UUID id) {
        Account account = get(id);
        if (account.status() != AccountStatus.ACTIVE) {
            throw DomainException.unprocessable("account " + id + " is " + account.status());
        }
        return account;
    }

    @Transactional
    public void transfer(UUID from, UUID to, BigDecimal amount) {
        if (!repository.debit(from, amount)) {
            throw DomainException.unprocessable("insufficient funds on account " + from);
        }
        repository.credit(to, amount);
    }
}
