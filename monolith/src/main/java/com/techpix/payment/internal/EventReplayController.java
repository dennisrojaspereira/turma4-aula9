package com.techpix.payment.internal;

import com.techpix.payment.EventReplayService;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Ferramenta de laboratório: republica o fato de um pagamento já existente, com o MESMO eventId.
 * Simula o que o Kafka faz de verdade em rebalanceamentos e retries: entregar o mesmo evento de novo.
 */
@RestController
@RequestMapping("/admin/events")
public class EventReplayController {

    private final EventReplayService replay;

    public EventReplayController(EventReplayService replay) {
        this.replay = replay;
    }

    @PostMapping("/replay/{paymentId}")
    public Map<String, Object> replay(@PathVariable UUID paymentId) {
        return replay.replay(paymentId);
    }
}
