package com.fabricmanagement.company.infrastructure.config;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Feign Client Configuration
 *
 * Configures Feign clients to propagate JWT tokens from incoming requests
 * to outgoing inter-service calls.
 * 
 * ✅ SECURITY:
 * - JWT token automatically forwarded to Contact Service
 * - Maintains authentication context across services
 */
@Configuration
public class FeignClientConfig {

    /**
     * JWT Token Propagation Interceptor
     *
     * Extracts the Authorization header from the current request context
     * and adds it to outgoing Feign requests.
     */
    @Bean
    public RequestInterceptor jwtTokenPropagationInterceptor() {
        return new RequestInterceptor() {
            @Override
            public void apply(RequestTemplate template) {
                ServletRequestAttributes attributes =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

                if (attributes != null) {
                    HttpServletRequest request = attributes.getRequest();
                    String authHeader = request.getHeader("Authorization");

                    if (authHeader != null && authHeader.startsWith("Bearer ")) {
                        template.header("Authorization", authHeader);
                    }
                }
            }
        };
    }
}

