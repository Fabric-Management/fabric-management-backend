package com.fabricmanagement.shared.infrastructure.resolver;

import com.fabricmanagement.shared.application.annotation.CurrentSecurityContext;
import com.fabricmanagement.shared.application.context.SecurityContext;
import com.fabricmanagement.shared.infrastructure.security.SecurityContextHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.MethodParameter;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Security Context Argument Resolver
 * 
 * Resolves @CurrentSecurityContext annotation on controller method parameters.
 * Extracts tenant ID and user ID from Spring Security context and creates SecurityContext object.
 * 
 * This resolver is automatically registered by WebMvcConfig.
 */
@Slf4j
@Component
public class SecurityContextResolver implements HandlerMethodArgumentResolver {

    /**
     * Check if this resolver supports the given parameter
     */
    @Override
    public boolean supportsParameter(@NonNull MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentSecurityContext.class)
                && parameter.getParameterType().equals(SecurityContext.class);
    }

    /**
     * Resolve the SecurityContext from Spring Security context
     */
    @Override
    @NonNull
    public Object resolveArgument(
            @NonNull MethodParameter parameter,
            @Nullable ModelAndViewContainer mavContainer,
            @NonNull NativeWebRequest webRequest,
            @Nullable WebDataBinderFactory binderFactory) {
        
        try {
            // Extract from SecurityContextHolder
            String role = SecurityContextHolder.getCurrentRole();
            
            return SecurityContext.builder()
                    .tenantId(SecurityContextHolder.getCurrentTenantId())
                    .userId(SecurityContextHolder.getCurrentUserId())
                    .roles(role != null ? new String[]{role} : new String[0])
                    .build();
                    
        } catch (Exception e) {
            log.error("Failed to resolve security context: {}", e.getMessage());
            // Return empty context if extraction fails
            // Controller will handle authorization failure
            return SecurityContext.builder().build();
        }
    }
}

