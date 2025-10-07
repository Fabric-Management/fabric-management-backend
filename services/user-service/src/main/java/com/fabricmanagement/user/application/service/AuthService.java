package com.fabricmanagement.user.application.service;

import com.fabricmanagement.shared.application.response.ApiResponse;
import com.fabricmanagement.shared.security.jwt.JwtTokenProvider;
import com.fabricmanagement.user.api.dto.*;
import com.fabricmanagement.user.domain.aggregate.User;
import com.fabricmanagement.user.domain.valueobject.UserStatus;
import com.fabricmanagement.user.infrastructure.client.ContactServiceClient;
import com.fabricmanagement.user.infrastructure.client.dto.ContactDto;
import com.fabricmanagement.user.infrastructure.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Authentication Service
 *
 * Handles user authentication operations
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final ContactServiceClient contactServiceClient;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    /**
     * Check if contact exists and whether user has password
     */
    @Transactional(readOnly = true)
    public CheckContactResponse checkContact(CheckContactRequest request) {
        log.debug("Checking contact: {}", request.getContactValue());

        try {
            // Find contact in contact service
            ApiResponse<List<ContactDto>> response = contactServiceClient.findByContactValue(request.getContactValue());
            List<ContactDto> contacts = response != null && response.getData() != null ? response.getData() : null;

            if (contacts == null || contacts.isEmpty()) {
                return CheckContactResponse.builder()
                        .exists(false)
                        .hasPassword(false)
                        .message("This contact is not registered. Please contact your administrator.")
                        .build();
            }

            // Get user by owner_id from contact
            ContactDto contact = contacts.get(0);
            UUID userId = UUID.fromString(contact.getOwnerId());

            User user = userRepository.findById(userId)
                    .filter(u -> !u.isDeleted())
                    .orElse(null);

            if (user == null) {
                return CheckContactResponse.builder()
                        .exists(false)
                        .hasPassword(false)
                        .message("User not found. Please contact your administrator.")
                        .build();
            }

            boolean hasPassword = user.getPasswordHash() != null && !user.getPasswordHash().isEmpty();

            return CheckContactResponse.builder()
                    .exists(true)
                    .hasPassword(hasPassword)
                    .userId(user.getId().toString())
                    .message(hasPassword ?
                            "Please enter your password" :
                            "Please create your password")
                    .build();

        } catch (Exception e) {
            log.error("Error checking contact: {}", e.getMessage(), e);
            return CheckContactResponse.builder()
                    .exists(false)
                    .hasPassword(false)
                    .message("An error occurred. Please try again.")
                    .build();
        }
    }

    /**
     * Setup initial password for user
     */
    @Transactional
    public void setupPassword(SetupPasswordRequest request) {
        log.info("Setting up password for contact: {}", request.getContactValue());

        // Find contact
        ApiResponse<List<ContactDto>> response = contactServiceClient.findByContactValue(request.getContactValue());
        List<ContactDto> contacts = response != null && response.getData() != null ? response.getData() : null;
        if (contacts == null || contacts.isEmpty()) {
            throw new RuntimeException("Contact not found");
        }

        ContactDto contact = contacts.get(0);
        UUID userId = UUID.fromString(contact.getOwnerId());

        // Get user
        User user = userRepository.findById(userId)
                .filter(u -> !u.isDeleted())
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Check if password already exists
        if (user.getPasswordHash() != null && !user.getPasswordHash().isEmpty()) {
            throw new RuntimeException("Password already set. Please use login.");
        }

        // Set password
        String hashedPassword = passwordEncoder.encode(request.getPassword());
        user.setPasswordHash(hashedPassword);
        user.setStatus(UserStatus.ACTIVE);
        user.setUpdatedBy(user.getId().toString());

        userRepository.save(user);

        log.info("Password setup completed for user: {}", userId);
    }

    /**
     * Login user with contact and password
     */
    @Transactional
    public LoginResponse login(LoginRequest request) {
        log.info("Login attempt for contact: {}", request.getContactValue());

        // Find contact
        ApiResponse<List<ContactDto>> response = contactServiceClient.findByContactValue(request.getContactValue());
        List<ContactDto> contacts = response != null && response.getData() != null ? response.getData() : null;
        if (contacts == null || contacts.isEmpty()) {
            throw new RuntimeException("Invalid credentials");
        }

        ContactDto contact = contacts.get(0);
        UUID userId = UUID.fromString(contact.getOwnerId());

        // Get user
        User user = userRepository.findById(userId)
                .filter(u -> !u.isDeleted())
                .orElseThrow(() -> new RuntimeException("Invalid credentials"));

        // Check password
        if (user.getPasswordHash() == null || user.getPasswordHash().isEmpty()) {
            throw new RuntimeException("Password not set. Please setup your password first.");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new RuntimeException("Invalid credentials");
        }

        // Check user status
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new RuntimeException("User account is not active");
        }

        // Update last login
        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);

        // Generate tokens
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", user.getRole());
        claims.put("firstName", user.getFirstName());
        claims.put("lastName", user.getLastName());

        String accessToken = jwtTokenProvider.generateToken(
                user.getId().toString(),
                user.getTenantId(),
                claims
        );

        String refreshToken = jwtTokenProvider.generateRefreshToken(
                user.getId().toString(),
                user.getTenantId()
        );

        log.info("Login successful for user: {}", userId);

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .userId(user.getId())
                .tenantId(UUID.fromString(user.getTenantId()))
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(contact.getContactValue())
                .role(user.getRole())
                .build();
    }
}
