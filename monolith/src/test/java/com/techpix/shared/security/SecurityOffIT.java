package com.techpix.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.techpix.support.AbstractIntegrationTest;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * O requisito inegociável do lab 22: sem o profile {@code secure}, nada muda.
 * Nenhum Keycloak no ar, nenhum token — e tudo responde como antes.
 */
class SecurityOffIT extends AbstractIntegrationTest {

    @Test
    void meReportsAnonymousWithoutSecureProfile() {
        ResponseEntity<Map> response = http.getForEntity("/me", Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("authenticated")).isEqualTo(false);
    }

    @Test
    void businessEndpointsNeedNoAuthentication() {
        // POST /accounts e POST /payments abertos, como sempre foram (scripts e labs anteriores dependem disto)
        UUID alice = openAccount("Alice Sem Auth", "100.00");
        UUID bob = openAccount("Bob Sem Auth", "0.00");

        ResponseEntity<Map> payment = pay(alice, bob, "10.00", "device-noauth");

        assertThat(payment.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void healthAndLoginPageAreOpen() {
        assertThat(http.getForEntity("/actuator/health", Map.class).getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> login = http.getForEntity("/login", String.class);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(login.getBody()).contains("Tech Pix");
    }
}
