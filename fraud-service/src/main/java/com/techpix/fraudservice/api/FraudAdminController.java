package com.techpix.fraudservice.api;

import com.techpix.fraudservice.history.LocalViewUpdater;
import com.techpix.fraudservice.history.ProcessedEvents;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Endpoints de laboratório do Fraud Service: ver a visão local e ligar/desligar a idempotência. */
@RestController
@RequestMapping("/admin/fraud")
public class FraudAdminController {

    public record IdempotencyRequest(boolean enabled) {
    }

    public record ChaosRequest(long latencyMs, double errorRate) {
    }

    private final ObjectProvider<LocalViewUpdater> updater;
    private final ObjectProvider<ProcessedEvents> processedEvents;
    private final ChaosFilter chaos;

    public FraudAdminController(ObjectProvider<LocalViewUpdater> updater, ObjectProvider<ProcessedEvents> processedEvents, ChaosFilter chaos) {
        this.updater = updater;
        this.processedEvents = processedEvents;
        this.chaos = chaos;
    }

    @GetMapping("/chaos")
    public Map<String, Object> chaos() {
        return chaos.status();
    }

    @PutMapping("/chaos")
    public Map<String, Object> changeChaos(@RequestBody ChaosRequest request) {
        chaos.set(new ChaosFilter.Chaos(request.latencyMs(), request.errorRate()));
        return chaos.status();
    }

    @GetMapping("/accounts/{accountId}/activity")
    public Map<String, Object> activity(@PathVariable UUID accountId) {
        LocalViewUpdater u = updater.getIfAvailable();
        return u == null ? Map.of("error", "local view disabled (fraud.history-source != LOCAL_VIEW)") : u.activityOf(accountId);
    }

    @GetMapping("/idempotency")
    public Map<String, Object> idempotency() {
        ProcessedEvents p = processedEvents.getIfAvailable();
        return Map.of("enabled", p != null && p.isEnabled());
    }

    @PutMapping("/idempotency")
    public Map<String, Object> changeIdempotency(@RequestBody IdempotencyRequest request) {
        ProcessedEvents p = processedEvents.getIfAvailable();
        if (p != null) {
            p.setEnabled(request.enabled());
        }
        return idempotency();
    }
}
