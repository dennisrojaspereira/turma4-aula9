package com.techpix.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.techpix.support.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

/**
 * Com o profile {@code secure}, a aplicação sobe SEM Keycloak no ar (endpoints explícitos:
 * nenhuma discovery no startup) e rotas de negócio passam a exigir token: 401 para API sem Bearer.
 * O fluxo OIDC completo não é testado aqui — é demonstração de aula (lab 22).
 */
@ActiveProfiles("secure")
class SecurityOnIT extends AbstractIntegrationTest {

    @Test
    void publicRoutesStayOpen() {
        ResponseEntity<Map> me = http.getForEntity("/me", Map.class);
        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(me.getBody().get("authenticated")).isEqualTo(false);

        assertThat(http.getForEntity("/actuator/health", Map.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(http.getForEntity("/login", String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void businessApiWithoutTokenIsUnauthorized() {
        ResponseEntity<Map> response = http.postForEntity("/accounts",
                Map.of("ownerName", "Sem Token", "initialBalance", new BigDecimal("10.00")), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
