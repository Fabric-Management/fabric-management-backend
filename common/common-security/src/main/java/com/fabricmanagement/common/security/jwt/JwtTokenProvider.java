package com.fabricmanagement.common.security.jwt;

import com.fabricmanagement.common.security.config.JwtProperties;
import com.fabricmanagement.common.security.exception.JwtTokenExpiredException;
import com.fabricmanagement.common.security.exception.JwtTokenInvalidException;
import com.fabricmanagement.common.security.model.AuthenticatedUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Component
public class JwtTokenProvider {

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private JwtProperties jwtProperties;

    public String createToken(String userId) {
        return jwtUtil.generateToken(userId);
    }

    public Authentication getAuthentication(String token) {
        try {
            String userId = jwtUtil.extractUserId(token);
            String tenantIdStr = jwtUtil.extractTenantId(token);
            String role = jwtUtil.extractRole(token);
            String email = jwtUtil.extractEmail(token);

            List<SimpleGrantedAuthority> authorities = Collections.emptyList();
            if (StringUtils.hasText(role)) {
                authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()));
            }

            AuthenticatedUser authenticatedUser = AuthenticatedUser.builder()
                    .userId(StringUtils.hasText(userId) ? UUID.fromString(userId) : null)
                    .username(userId)
                    .email(email)
                    .tenantId(StringUtils.hasText(tenantIdStr) ? UUID.fromString(tenantIdStr) : null)
                    .role(role)
                    .authorities(authorities)
                    .build();

            return new UsernamePasswordAuthenticationToken(authenticatedUser, "", authorities);
        } catch (JwtTokenExpiredException | JwtTokenInvalidException e) {
            throw e;
        } catch (Exception e) {
            throw new JwtTokenInvalidException("Failed to parse JWT token", e);
        }
    }

    public boolean validateToken(String token) {
        try {
            String userId = jwtUtil.extractUserId(token);
            return jwtUtil.validateToken(token, userId);
        } catch (JwtTokenExpiredException | JwtTokenInvalidException e) {
            return false;
        }
    }

    public String getUserIdFromToken(String token) {
        return jwtUtil.extractUserId(token);
    }
}
