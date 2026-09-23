package com.techpix.ledger;

import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class LedgerRepository {

    private final JdbcClient jdbc;

    public LedgerRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(LedgerEntry entry) {
        jdbc.sql("""
                INSERT INTO ledger_entries (id, payment_id, account_id, entry_type, amount, created_at)
                VALUES (:id, :paymentId, :accountId, :type, :amount, :createdAt)
                """)
                .param("id", entry.id())
                .param("paymentId", entry.paymentId())
                .param("accountId", entry.accountId())
                .param("type", entry.type().name())
                .param("amount", entry.amount())
                .param("createdAt", Timestamp.from(entry.createdAt()))
                .update();
    }

    public List<LedgerEntry> findByPayment(UUID paymentId) {
        return jdbc.sql("SELECT * FROM ledger_entries WHERE payment_id = :paymentId ORDER BY entry_type")
                .param("paymentId", paymentId)
                .query((rs, i) -> new LedgerEntry(
                        rs.getObject("id", UUID.class),
                        rs.getObject("payment_id", UUID.class),
                        rs.getObject("account_id", UUID.class),
                        LedgerEntry.EntryType.valueOf(rs.getString("entry_type")),
                        rs.getBigDecimal("amount"),
                        rs.getTimestamp("created_at").toInstant()))
                .list();
    }
}
