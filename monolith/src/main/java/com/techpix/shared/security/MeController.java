package com.techpix.shared.security;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Quem sou eu? Responde para a sessão OIDC (login no navegador), para Bearer token (scripts)
 * e para anônimo (profile default, sem Keycloak): {authenticated:false}.
 */
@RestController
public class MeController {

    @GetMapping("/me")
    public Map<String, Object> me(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return Map.of("authenticated", false);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("authenticated", true);
        if (authentication.getPrincipal() instanceof OidcUser oidc) {
            body.put("name", oidc.getPreferredUsername() != null ? oidc.getPreferredUsername() : oidc.getName());
            body.put("email", oidc.getEmail());
        } else if (authentication instanceof JwtAuthenticationToken jwt) {
            String username = jwt.getToken().getClaimAsString("preferred_username");
            body.put("name", username != null ? username : jwt.getName());
            body.put("email", jwt.getToken().getClaimAsString("email"));
        } else {
            body.put("name", authentication.getName());
            body.put("email", null);
        }
        body.put("roles", roles(authentication));
        return body;
    }

    private List<String> roles(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.substring("ROLE_".length()))
                .sorted()
                .toList();
    }
}
