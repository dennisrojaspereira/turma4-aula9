package com.techpix.shared.security;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

/**
 * A segurança da Tech Pix, ligada apenas com o profile {@code secure} (lab 22).
 * <p>
 * Dois caminhos de autenticação no mesmo filter chain: o navegador faz login OIDC no Keycloak
 * (authorization code, sessão) e scripts/APIs mandam {@code Authorization: Bearer <JWT>}.
 * Roles do realm viram {@code ROLE_techpix-admin} / {@code ROLE_techpix-user} — minúsculas,
 * exatamente como no Keycloak.
 */
@Configuration
@EnableWebSecurity
@Profile("secure")
public class SecurityConfig {

    private final String keycloakPublicUrl;

    public SecurityConfig(@Value("${techpix.keycloak.public-url:http://localhost:8180}") String keycloakPublicUrl) {
        this.keycloakPublicUrl = keycloakPublicUrl;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, ClientRegistrationRepository registrations) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        // paginas publicas e o minimo de observabilidade (Prometheus faz scrape sem token)
                        .requestMatchers("/", "/index.html", "/login", "/login.html", "/css/**", "/favicon.ico", "/error", "/me").permitAll()
                        .requestMatchers("/actuator/health/**", "/actuator/health", "/actuator/info", "/actuator/prometheus").permitAll()
                        // rotas administrativas exigem a realm role techpix-admin
                        .requestMatchers("/admin/**").hasAuthority("ROLE_techpix-admin")
                        .anyRequest().authenticated())
                // CSRF fica ligado para o fluxo web (sessao), mas as APIs chamadas por script com
                // Bearer token nao tem como mandar o token CSRF — ficam fora.
                .csrf(csrf -> csrf.ignoringRequestMatchers("/payments/**", "/payments", "/accounts/**", "/accounts", "/admin/**"))
                .oauth2Login(oauth -> oauth
                        .loginPage("/login")
                        .defaultSuccessUrl("/")
                        .userInfoEndpoint(userInfo -> userInfo.userAuthoritiesMapper(realmRolesMapper())))
                .oauth2ResourceServer(rs -> rs.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtConverter())))
                // Logout por GET para a home poder usar um link simples, sem token CSRF.
                // Trade-off de laboratorio: GET /logout e vulneravel a logout forcado via link.
                .logout(logout -> logout
                        .logoutRequestMatcher(PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET, "/logout"))
                        .logoutSuccessHandler(keycloakLogoutHandler(registrations)));
        return http.build();
    }

    /** Bearer token (resource server): claims do JWT viram ROLE_*. */
    private JwtAuthenticationConverter jwtConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new KeycloakRoleConverter());
        return converter;
    }

    /** Login OIDC (sessao): roles vem da claim "roles" do ID token (protocol mapper do client). */
    private GrantedAuthoritiesMapper realmRolesMapper() {
        return authorities -> {
            Set<GrantedAuthority> mapped = new LinkedHashSet<>(authorities);
            for (GrantedAuthority authority : authorities) {
                if (authority instanceof OidcUserAuthority oidc) {
                    mapped.addAll(KeycloakRoleConverter.authoritiesFrom(oidc.getIdToken().getClaims()));
                }
            }
            return mapped;
        };
    }

    /**
     * Logout no Keycloak alem da sessao local. Sem discovery (usamos endpoints explicitos,
     * ver application-secure.yml), o end_session_endpoint nao vem nos metadados do provider:
     * injetamos a URL publica na mao, porque quem navega ate ela e o navegador.
     */
    private LogoutSuccessHandler keycloakLogoutHandler(ClientRegistrationRepository registrations) {
        ClientRegistrationRepository withEndSession = registrationId -> {
            ClientRegistration registration = registrations.findByRegistrationId(registrationId);
            if (registration == null) {
                return null;
            }
            return ClientRegistration.withClientRegistration(registration)
                    .providerConfigurationMetadata(Map.of("end_session_endpoint",
                            keycloakPublicUrl + "/realms/techpix/protocol/openid-connect/logout"))
                    .build();
        };
        OidcClientInitiatedLogoutSuccessHandler handler = new OidcClientInitiatedLogoutSuccessHandler(withEndSession);
        handler.setPostLogoutRedirectUri("{baseUrl}");
        return handler;
    }
}
