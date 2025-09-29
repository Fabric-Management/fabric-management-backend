package com.fabricmanagement.identity.infrastructure.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Single Responsibility: User details loading only
 * Open/Closed: Can be extended without modification
 * Custom user details service for authentication
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CustomUserDetailsService implements UserDetailsService {

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        log.info("Loading user details for username: {}", username);
        
        // Implementation would load user details from external service
        // For now, return a mock user
        return UserPrincipal.create(
            "user123",
            username,
            "user@example.com",
            "USER",
            "ACTIVE"
        );
    }
}