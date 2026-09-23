package com.techpix.fraudservice.health;

import com.techpix.fraudservice.acl.CachedBlacklist;
import com.techpix.fraudservice.config.FraudServiceProperties;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * "Pode receber tráfego?" Só depois de aquecer.
 * <p>
 * O processo sobe em segundos, mas o serviço só está pronto quando a blacklist está em memória e o
 * warm-up (simulado por {@code fraud.warmup-seconds}) terminou. Até lá:
 * <ul>
 *   <li>liveness = UP (o processo está vivo, não reinicie);</li>
 *   <li>readiness = DOWN (não mande tráfego ainda).</li>
 * </ul>
 * Kubernetes usa essa diferença para não rotear para um Pod que ainda não sabe responder. Ver lab 12.
 */
@Component("warmup")
public class WarmupReadiness implements HealthIndicator {

    private static final Logger log = LoggerFactory.getLogger(WarmupReadiness.class);

    private final CachedBlacklist blacklist;
    private final Duration warmup;
    private volatile Instant readyAt;

    public WarmupReadiness(CachedBlacklist blacklist, FraudServiceProperties props) {
        this.blacklist = blacklist;
        this.warmup = Duration.ofSeconds(props.warmupSeconds());
    }

    @EventListener(ApplicationReadyEvent.class)
    void startWarmup() {
        readyAt = Instant.now().plus(warmup);
        log.info("warm-up de {}s iniciado; readiness ficara DOWN ate {}", warmup.toSeconds(), readyAt);
    }

    @Override
    public Health health() {
        boolean warmedUp = readyAt != null && !Instant.now().isBefore(readyAt);
        boolean loaded = blacklist.isLoaded();
        Health.Builder status = (warmedUp && loaded) ? Health.up() : Health.down();
        return status
                .withDetail("blacklistLoaded", loaded)
                .withDetail("blacklistEntries", blacklist.size())
                .withDetail("warmedUp", warmedUp)
                .withDetail("readyAt", readyAt == null ? "not started" : readyAt.toString())
                .build();
    }
}
