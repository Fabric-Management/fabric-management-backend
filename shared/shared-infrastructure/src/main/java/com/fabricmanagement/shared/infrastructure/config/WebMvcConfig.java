package com.fabricmanagement.shared.infrastructure.config;

import com.fabricmanagement.shared.infrastructure.resolver.SecurityContextResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * Web MVC Configuration
 * 
 * Registers custom argument resolvers and other web configuration.
 * This configuration is automatically applied to all microservices using shared-infrastructure module.
 * 
 * @EnableMethodSecurity enables @PreAuthorize annotation support
 */
@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final SecurityContextResolver securityContextResolver;

    /**
     * Add custom argument resolvers
     * 
     * Registers SecurityContextResolver to enable @CurrentSecurityContext annotation
     */
    @Override
    public void addArgumentResolvers(@NonNull List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(securityContextResolver);
    }
}

