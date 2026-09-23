package com.techpix.shared.admin;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * Gera o "passado" da Tech Pix: contas, dispositivos, um histórico grande de pagamentos
 * e uma blacklist. É o que transforma tabelas vazias em tabelas que doem.
 * <p>
 * Determinístico (seed fixo) para que dois alunos vejam números parecidos.
 */
@Service
public class SeedService {

    private static final Logger log = LoggerFactory.getLogger(SeedService.class);
    private static final int BATCH = 5_000;

    public record SeedSummary(int accounts, int payments, int blacklist, long totalPayments) {
    }

    private final JdbcTemplate jdbcTemplate;
    private final JdbcClient jdbc;
    private final Clock clock;

    public SeedService(JdbcTemplate jdbcTemplate, JdbcClient jdbc, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public SeedSummary seed(int accountCount, int paymentCount, int blacklistCount) {
        Random random = new Random(42);
        Instant now = Instant.now(clock);
        Instant start = now.minus(Duration.ofDays(30));

        List<UUID> accounts = new ArrayList<>(accountCount);
        List<Object[]> accountRows = new ArrayList<>(accountCount);
        for (int i = 0; i < accountCount; i++) {
            UUID id = UUID.randomUUID();
            accounts.add(id);
            accountRows.add(new Object[]{id, "seed-owner-" + i, new BigDecimal("1000000.00"), "ACTIVE",
                    Timestamp.from(start.minus(Duration.ofDays(random.nextInt(365))))});
        }
        jdbcTemplate.batchUpdate("INSERT INTO accounts (id, owner_name, balance, status, created_at) VALUES (?, ?, ?, ?, ?)", accountRows);

        // Poucos dispositivos compartilhados por muitas contas: e o que device fingerprint procura.
        int deviceCount = Math.max(10, accountCount / 2);
        long windowSeconds = Duration.between(start, now).getSeconds();
        List<Object[]> batch = new ArrayList<>(BATCH);
        for (int i = 0; i < paymentCount; i++) {
            UUID payer = accounts.get(random.nextInt(accounts.size()));
            UUID payee = accounts.get(random.nextInt(accounts.size()));
            BigDecimal amount = BigDecimal.valueOf(1 + random.nextInt(50_000), 2);
            String device = "seed-device-" + random.nextInt(deviceCount);
            String status = random.nextInt(100) < 4 ? "REJECTED" : "APPROVED";
            Instant createdAt = start.plusSeconds((long) (random.nextDouble() * windowSeconds));
            batch.add(new Object[]{UUID.randomUUID(), payer, payee, amount, device, status, Timestamp.from(createdAt)});
            if (batch.size() == BATCH) {
                flush(batch);
            }
        }
        flush(batch);

        List<Object[]> blacklistRows = new ArrayList<>(blacklistCount);
        for (int i = 0; i < blacklistCount; i++) {
            boolean account = random.nextBoolean();
            blacklistRows.add(new Object[]{UUID.randomUUID(), account ? "ACCOUNT" : "DEVICE",
                    account ? UUID.randomUUID().toString() : "banned-device-" + i, "seed", Timestamp.from(now)});
        }
        jdbcTemplate.batchUpdate("INSERT INTO fraud_blacklist (id, kind, value, reason, created_at) VALUES (?, ?, ?, ?, ?)", blacklistRows);

        long total = jdbc.sql("SELECT count(*) FROM payments").query(Long.class).single();
        log.info("seed accounts={} payments={} blacklist={} totalPayments={}", accountCount, paymentCount, blacklistCount, total);
        return new SeedSummary(accountCount, paymentCount, blacklistCount, total);
    }

    private void flush(List<Object[]> batch) {
        if (batch.isEmpty()) {
            return;
        }
        jdbcTemplate.batchUpdate("""
                INSERT INTO payments (id, payer_account_id, payee_account_id, amount, device_id, status, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, batch);
        batch.clear();
    }

    /** Ids de contas "seed", para o gerador de carga escolher pagadores e destinatários. */
    public List<UUID> seedAccountIds(int limit) {
        return jdbc.sql("SELECT id FROM accounts WHERE owner_name LIKE 'seed-owner-%' ORDER BY owner_name LIMIT :limit")
                .param("limit", limit)
                .query(UUID.class)
                .list();
    }
}
