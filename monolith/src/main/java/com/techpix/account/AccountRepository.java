package com.techpix.account;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class AccountRepository {

    private final JdbcClient jdbc;

    public AccountRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(Account account) {
        jdbc.sql("""
                INSERT INTO accounts (id, owner_name, balance, status, created_at)
                VALUES (:id, :owner, :balance, :status, :createdAt)
                """)
                .param("id", account.id())
                .param("owner", account.ownerName())
                .param("balance", account.balance())
                .param("status", account.status().name())
                .param("createdAt", Timestamp.from(account.createdAt()))
                .update();
    }

    public Optional<Account> findById(UUID id) {
        return jdbc.sql("SELECT id, owner_name, balance, status, created_at FROM accounts WHERE id = :id")
                .param("id", id)
                .query((rs, i) -> new Account(
                        rs.getObject("id", UUID.class),
                        rs.getString("owner_name"),
                        rs.getBigDecimal("balance"),
                        AccountStatus.valueOf(rs.getString("status")),
                        rs.getTimestamp("created_at").toInstant()))
                .optional();
    }

    /** Debita apenas se houver saldo: a condição no UPDATE evita saldo negativo sob concorrência. */
    public boolean debit(UUID id, BigDecimal amount) {
        return jdbc.sql("UPDATE accounts SET balance = balance - :amount WHERE id = :id AND balance >= :amount")
                .param("amount", amount)
                .param("id", id)
                .update() == 1;
    }

    public void credit(UUID id, BigDecimal amount) {
        jdbc.sql("UPDATE accounts SET balance = balance + :amount WHERE id = :id")
                .param("amount", amount)
                .param("id", id)
                .update();
    }
}
