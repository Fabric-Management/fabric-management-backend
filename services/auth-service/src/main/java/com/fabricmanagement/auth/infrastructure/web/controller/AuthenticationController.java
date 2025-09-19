package com.fabricmanagement.auth.infrastructure.web.controller;

import com.fabricmanagement.auth.application.service.SessionService;
import com.fabricmanagement.auth.domain.model.UserSession;
import com.fabricmanagement.auth.infrastructure.web.dto.LoginRequest;
import com.fabricmanagement.auth.infrastructure.web.dto.LoginResponse;
import com.fabricmanagement.auth.infrastructure.web.dto.RefreshTokenRequest;
import com.fabricmanagement.auth.infrastructure.web.dto.RefreshTokenResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;

/**
 * REST controller for authentication operations.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthenticationController {
    
    private final SessionService sessionService;
    
    /**
     * Logs in a user and creates a session.
     */
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        log.info("Login attempt for user: {}", request.getUsername());
        
        try {
            // TODO: Integrate with identity-service for user authentication
            // For now, we'll create a mock session
            UUID userId = UUID.randomUUID();
            String username = request.getUsername();
            String email = request.getEmail();
            String role = "USER";
            UUID tenantId = UUID.randomUUID();
            
            String ipAddress = getClientIpAddress(httpRequest);
            String userAgent = httpRequest.getHeader("User-Agent");
            
            UserSession session = sessionService.createSession(
                userId, username, email, role, tenantId, ipAddress, userAgent
            );
            
            LoginResponse response = LoginResponse.builder()
                .accessToken(session.getAccessToken())
                .refreshToken(session.getRefreshToken())
                .tokenType("Bearer")
                .expiresIn(3600) // 1 hour
                .userId(userId)
                .username(username)
                .email(email)
                .role(role)
                .tenantId(tenantId)
                .build();
            
            log.info("Login successful for user: {}", username);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Login failed for user: {}", request.getUsername(), e);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(LoginResponse.builder()
                    .error("Invalid credentials")
                    .build());
        }
    }
    
    /**
     * Refreshes an access token.
     */
    @PostMapping("/refresh")
    public ResponseEntity<RefreshTokenResponse> refreshToken(@RequestBody RefreshTokenRequest request) {
        log.info("Refresh token request");
        
        try {
            UserSession session = sessionService.refreshSession(request.getRefreshToken());
            
            RefreshTokenResponse response = RefreshTokenResponse.builder()
                .accessToken(session.getAccessToken())
                .tokenType("Bearer")
                .expiresIn(3600) // 1 hour
                .build();
            
            log.info("Token refreshed successfully");
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Token refresh failed", e);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(RefreshTokenResponse.builder()
                    .error("Invalid refresh token")
                    .build());
        }
    }
    
    /**
     * Logs out a user and revokes the session.
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestHeader("Authorization") String authorization) {
        log.info("Logout request");
        
        try {
            String accessToken = authorization.replace("Bearer ", "");
            sessionService.revokeSession(accessToken);
            
            log.info("Logout successful");
            return ResponseEntity.noContent().build();
            
        } catch (Exception e) {
            log.error("Logout failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Validates a session.
     */
    @GetMapping("/validate")
    public ResponseEntity<Boolean> validateSession(@RequestHeader("Authorization") String authorization) {
        log.debug("Session validation request");
        
        try {
            String accessToken = authorization.replace("Bearer ", "");
            boolean isValid = sessionService.validateSession(accessToken);
            
            return ResponseEntity.ok(isValid);
            
        } catch (Exception e) {
            log.error("Session validation failed", e);
            return ResponseEntity.ok(false);
        }
    }
    
    /**
     * Gets active sessions for a user.
     */
    @GetMapping("/sessions")
    public ResponseEntity<List<UserSession>> getUserSessions(@RequestParam UUID userId) {
        log.info("Getting sessions for user: {}", userId);
        
        try {
            List<UserSession> sessions = sessionService.getActiveUserSessions(userId);
            return ResponseEntity.ok(sessions);
            
        } catch (Exception e) {
            log.error("Failed to get user sessions", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Revokes all sessions for a user.
     */
    @PostMapping("/sessions/revoke-all")
    public ResponseEntity<Void> revokeAllUserSessions(@RequestParam UUID userId) {
        log.info("Revoking all sessions for user: {}", userId);
        
        try {
            sessionService.revokeAllUserSessions(userId);
            return ResponseEntity.noContent().build();
            
        } catch (Exception e) {
            log.error("Failed to revoke all user sessions", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Gets client IP address from request.
     */
    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        
        return request.getRemoteAddr();
    }
}
