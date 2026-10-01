package com.techpix.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/** O mapeamento de roles do Keycloak para authorities, sem contexto Spring. */
class KeycloakRoleConverterTest {

    @Test
    void mapsRealmAccessRolesToAuthorities() {
        var authorities = KeycloakRoleConverter.authoritiesFrom(
                Map.of("realm_access", Map.of("roles", List.of("techpix-admin", "techpix-user"))));

        assertThat(authorities).extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_techpix-admin", "ROLE_techpix-user");
    }

    @Test
    void mapsCustomRolesClaimAndDeduplicates() {
        var authorities = KeycloakRoleConverter.authoritiesFrom(Map.of(
                "roles", List.of("techpix-user"),
                "realm_access", Map.of("roles", List.of("techpix-user"))));

        assertThat(authorities).extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_techpix-user");
    }

    @Test
    void emptyClaimsMeanNoAuthorities() {
        assertThat(KeycloakRoleConverter.authoritiesFrom(Map.of())).isEmpty();
        assertThat(KeycloakRoleConverter.authoritiesFrom(Map.of("realm_access", Map.of()))).isEmpty();
    }

    @Test
    void convertsAFullJwt() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .claim("roles", List.of("techpix-admin"))
                .build();

        assertThat(new KeycloakRoleConverter().convert(jwt))
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_techpix-admin");
    }
}
