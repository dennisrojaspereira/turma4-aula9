package com.techpix.fraudservice.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.techpix.fraudservice.support.AbstractFraudServiceIT;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Pod Running != aplicação pronta.
 * Com warm-up de 3 s: liveness UP desde o início, readiness DOWN até o warm-up terminar.
 */
class HealthProbesIT extends AbstractFraudServiceIT {

    @DynamicPropertySource
    static void warmup(DynamicPropertyRegistry registry) {
        registry.add("fraud.warmup-seconds", () -> 3);
    }

    @Test
    void livenessIsUpWhileReadinessWaitsForWarmup() {
        ResponseEntity<Map> liveness = http.getForEntity("/actuator/health/liveness", Map.class);
        ResponseEntity<Map> readiness = http.getForEntity("/actuator/health/readiness", Map.class);

        assertThat(liveness.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(liveness.getBody().get("status")).isEqualTo("UP");
        assertThat(readiness.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(readiness.getBody().get("status")).isEqualTo("DOWN");

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            ResponseEntity<Map> ready = http.getForEntity("/actuator/health/readiness", Map.class);
            assertThat(ready.getStatusCode()).isEqualTo(HttpStatus.OK);
        });
    }
}
