package com.fabricmanagement.user.infrastructure.config;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Feign Client Configuration
 *
 * Configures Feign clients to propagate JWT tokens from incoming requests
 * to outgoing inter-service calls.
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
