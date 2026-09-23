package com.techpix.fraudservice.acl;

import com.techpix.fraudservice.domain.Blacklist;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Blacklist em memória, carregada da tabela {@code fraud_blacklist} e atualizada periodicamente.
 * <p>
 * A tabela hoje mora no banco compartilhado, mas seu dono é Fraud. Quando o Fraud Service
 * ganhar seu próprio banco, ela vai junto. Por isso esta classe não é "Legacy": o dado é nosso.
 */
@Component
public class CachedBlacklist implements Blacklist {

    private static final Logger log = LoggerFactory.getLogger(CachedBlacklist.class);

    private final JdbcClient jdbc;
    private final AtomicReference<Set<String>> keys = new AtomicReference<>(Set.of());
    private volatile boolean loaded;

    public CachedBlacklist(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Scheduled(initialDelay = 0, fixedDelayString = "${fraud.blacklist-refresh-ms:30000}")
    public void refresh() {
        Set<String> fresh = Set.copyOf(jdbc.sql("SELECT kind || ':' || value FROM fraud_blacklist").query(String.class).list());
        keys.set(fresh);
        loaded = true;
        log.debug("blacklist refreshed entries={}", fresh.size());
    }

    public boolean isLoaded() {
        return loaded;
    }

    public int size() {
        return keys.get().size();
    }

    @Override
    public boolean containsAccount(String accountId) {
        return keys.get().contains("ACCOUNT:" + accountId);
    }

    @Override
    public boolean containsDevice(String deviceId) {
        return keys.get().contains("DEVICE:" + deviceId);
    }
}
