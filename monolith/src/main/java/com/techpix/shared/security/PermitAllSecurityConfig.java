package com.techpix.shared.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.Customizer;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Comportamento padrão: tudo aberto, como a Tech Pix sempre foi antes da dependência de security.
 * Os 69 testes e todos os scripts dos labs anteriores continuam funcionando sem Keycloak.
 * A segurança liga com SPRING_PROFILES_ACTIVE=secure (lab 22).
 */
@Configuration
@EnableWebSecurity
@Profile("!secure")
public class PermitAllSecurityConfig {

    @Bean
    SecurityFilterChain permitAllFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .csrf(csrf -> csrf.disable())
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .logout(Customizer.withDefaults());
        return http.build();
    }
}
