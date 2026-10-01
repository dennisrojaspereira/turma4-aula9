package com.techpix.shared.security;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * /login (sem extensão) serve a página estática login.html — vale nos dois profiles.
 * Sem o profile secure a tela aparece, mas o botão só completa o fluxo com Keycloak no ar.
 */
@Configuration
public class LoginRouteConfig implements WebMvcConfigurer {

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/login").setViewName("forward:/login.html");
    }
}
