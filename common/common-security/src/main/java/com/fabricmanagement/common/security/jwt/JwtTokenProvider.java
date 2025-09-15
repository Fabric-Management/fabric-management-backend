package com.fabricmanagement.common.security.jwt;

import com.fabricmanagement.common.security.config.JwtProperties;
import com.fabricmanagement.common.security.exception.JwtTokenExpiredException;
import com.fabricmanagement.common.security.exception.JwtTokenInvalidException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

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
            List<SimpleGrantedAuthority> authorities = Collections.emptyList(); // Can be extended with roles
            return new UsernamePasswordAuthenticationToken(userId, "", authorities);
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
