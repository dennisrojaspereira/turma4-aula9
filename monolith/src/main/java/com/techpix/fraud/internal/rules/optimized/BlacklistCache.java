package com.techpix.fraud.internal.rules.optimized;

import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * A blacklist inteira em memória, atualizada periodicamente.
 * <p>
 * Trade-off explícito: uma conta banida agora pode levar até {@code refresh} para ser bloqueada.
 * Para uma blacklist isso costuma ser aceitável. Para um saldo, nunca seria.
 */
@Component
public class BlacklistCache {

    private static final Logger log = LoggerFactory.getLogger(BlacklistCache.class);

    private final FraudHistoryAggregateRepository repository;
    private final AtomicReference<Set<String>> keys = new AtomicReference<>(Set.of());
    private volatile boolean loaded;

    public BlacklistCache(FraudHistoryAggregateRepository repository) {
        this.repository = repository;
    }

    @Scheduled(initialDelay = 0, fixedDelayString = "${techpix.fraud.blacklist-refresh-ms:30000}")
    public void refresh() {
        Set<String> fresh = Set.copyOf(repository.allBlacklistKeys());
        keys.set(fresh);
        loaded = true;
        log.debug("blacklist cache refreshed entries={}", fresh.size());
    }

    public boolean contains(String kind, String value) {
        if (!loaded) {
            refresh();
        }
        return keys.get().contains(kind + ":" + value);
    }

    public int size() {
        return keys.get().size();
    }
}
