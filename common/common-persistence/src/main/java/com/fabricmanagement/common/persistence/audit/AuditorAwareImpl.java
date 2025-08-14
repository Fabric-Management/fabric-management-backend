package com.fabricmanagement.common.persistence.audit;

import org.springframework.data.domain.AuditorAware;

import java.util.Optional;

public class AuditorAwareImpl implements AuditorAware<String> {

    @Override
    public Optional<String> getCurrentAuditor() {
        // TODO: Get from SecurityContext when security is implemented
        // For now, return a default value
        return Optional.of("system");

        // Future implementation:
        // return Optional.ofNullable(SecurityContextHolder.getContext())
        //         .map(SecurityContext::getAuthentication)
        //         .filter(Authentication::isAuthenticated)
        //         .map(Authentication::getName);
    }
}