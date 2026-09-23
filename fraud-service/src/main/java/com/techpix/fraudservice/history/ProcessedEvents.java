package com.techpix.fraudservice.history;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Idempotência do consumidor: "já vi este eventId?".
 * <p>
 * Kafka entrega pelo menos uma vez. Rebalanceamento, retry, restart do consumidor antes do commit do
 * offset: o mesmo evento chega de novo. Sem esta tabela, {@code account_activity} conta em dobro.
 * <p>
 * {@code INSERT ... ON CONFLICT DO NOTHING} dentro da mesma transação da atualização de estado:
 * ou os dois acontecem, ou nenhum. É isso que torna a operação idempotente de verdade.
 * <p>
 * A chave {@code enabled} existe só para o laboratório mostrar o problema antes da solução.
 */
@Component
@ConditionalOnProperty(name = "fraud.history-source", havingValue = "LOCAL_VIEW")
public class ProcessedEvents {

    private final JdbcClient jdbc;
    private final Clock clock;
    private final AtomicBoolean enabled;

    public ProcessedEvents(JdbcClient jdbc, Clock clock,
                           @org.springframework.beans.factory.annotation.Value("${fraud.idempotency.enabled:true}") boolean enabled) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.enabled = new AtomicBoolean(enabled);
    }

    /** @return true se o evento é novo e deve ser aplicado; false se já foi processado. */
    public boolean markProcessed(UUID eventId, String type) {
        if (!enabled.get()) {
            return true;   // laboratorio: idempotencia desligada, tudo e "novo"
        }
        int inserted = jdbc.sql("INSERT INTO processed_events (event_id, event_type, processed_at) VALUES (:id, :type, :at) ON CONFLICT DO NOTHING")
                .param("id", eventId).param("type", type).param("at", Timestamp.from(Instant.now(clock)))
                .update();
        return inserted == 1;
    }

    public boolean isEnabled() {
        return enabled.get();
    }

    public void setEnabled(boolean value) {
        enabled.set(value);
    }
}
