package com.fabricmanagement.identity.infrastructure.web.controller;

import com.fabricmanagement.identity.application.service.AuthenticationService;
import com.fabricmanagement.identity.domain.model.User;
import com.fabricmanagement.identity.domain.valueobject.Role;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.UUID;

/**
 * REST controller for user authentication and identity management.
 */
@RestController
@RequestMapping("/api/identity")
@RequiredArgsConstructor
@Slf4j
public class AuthenticationController {
    
    private final AuthenticationService authenticationService;
    
    /**
     * Authenticates a user.
     */
    @PostMapping("/authenticate")
    public ResponseEntity<User> authenticateUser(@RequestBody AuthenticateRequest request) {
        log.info("Authenticating user: {}", request.getUsernameOrEmail());
        
        try {
            var userOpt = authenticationService.authenticateUser(
                request.getUsernameOrEmail(),
                request.getPassword()
            );
            
            if (userOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
            
            User user = userOpt.get();
            log.info("User authenticated successfully: {}", user.getUsername());
            return ResponseEntity.ok(user);
            
        } catch (Exception e) {
            log.error("Authentication failed for user: {}", request.getUsernameOrEmail(), e);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }
    
    /**
     * Gets user by ID.
     */
    @GetMapping("/users/{userId}")
    public ResponseEntity<User> getUserById(@PathVariable UUID userId) {
        log.info("Getting user by ID: {}", userId);
        
        try {
            var userOpt = authenticationService.getUserById(userId);
            
            if (userOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            
            User user = userOpt.get();
            return ResponseEntity.ok(user);
            
        } catch (Exception e) {
            log.error("Failed to get user: {}", userId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * DTO for authenticate request.
     */
    public static class AuthenticateRequest {
        private String usernameOrEmail;
        private String password;
        
        // Getters and setters
        public String getUsernameOrEmail() { return usernameOrEmail; }
        public void setUsernameOrEmail(String usernameOrEmail) { this.usernameOrEmail = usernameOrEmail; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }
}