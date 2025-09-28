package com.fabricmanagement.identity.infrastructure.security;

import com.fabricmanagement.identity.domain.model.User;
import com.fabricmanagement.identity.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Custom UserDetailsService implementation for Spring Security.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        log.debug("Loading user by username: {}", username);
        
        User user = userRepository.findByUsername(username)
            .orElseThrow(() -> new UsernameNotFoundException("User not found with username: " + username));

        return createUserPrincipal(user);
    }

    @Transactional(readOnly = true)
    public UserDetails loadUserByEmail(String email) throws UsernameNotFoundException {
        log.debug("Loading user by email: {}", email);
        
        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + email));

        return createUserPrincipal(user);
    }

    @Transactional(readOnly = true)
    public UserDetails loadUserByContact(String contactValue) throws UsernameNotFoundException {
        log.debug("Loading user by contact: {}", contactValue);
        
        User user = userRepository.findByContactValue(contactValue)
            .orElseThrow(() -> new UsernameNotFoundException("User not found with contact: " + contactValue));

        return createUserPrincipal(user);
    }

    private UserPrincipal createUserPrincipal(User user) {
        Collection<? extends GrantedAuthority> authorities = getAuthorities(user);
        
        return UserPrincipal.builder()
            .id(user.getId().getValue())
            .username(user.getUsername())
            .email(user.getPrimaryEmail())
            .password(user.getCredentials() != null ? user.getCredentials().getPasswordHash() : "")
            .authorities(authorities)
            .accountNonExpired(true)
            .accountNonLocked(!user.isAccountLocked())
            .credentialsNonExpired(true)
            .enabled(user.getStatus() == com.fabricmanagement.identity.domain.valueobject.UserStatus.ACTIVE)
            .twoFactorEnabled(user.isTwoFactorEnabled())
            .build();
    }

    private Collection<? extends GrantedAuthority> getAuthorities(User user) {
        String role = "ROLE_" + user.getRole().name();
        return Collections.singletonList(new SimpleGrantedAuthority(role));
    }
}
