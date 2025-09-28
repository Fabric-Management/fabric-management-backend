package com.fabricmanagement.identity.application.service;

import com.fabricmanagement.identity.application.dto.auth.LoginRequest;
import com.fabricmanagement.identity.application.dto.auth.RegisterRequest;
import com.fabricmanagement.identity.application.dto.auth.AuthResponse;
import com.fabricmanagement.identity.domain.model.User;
import com.fabricmanagement.identity.domain.repository.UserRepository;
import com.fabricmanagement.identity.infrastructure.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AuthenticationService.
 */
@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private JwtTokenProvider tokenProvider;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private NotificationService notificationService;

    @Mock
    private SessionService sessionService;

    @InjectMocks
    private AuthenticationService authenticationService;

    @Test
    void shouldRegisterUserSuccessfully() {
        // Given
        RegisterRequest request = RegisterRequest.builder()
            .username("testuser")
            .firstName("John")
            .lastName("Doe")
            .email("test@example.com")
            .phone("+1234567890")
            .password("TestPassword123!")
            .build();

        when(userRepository.existsByUsername(anyString())).thenReturn(false);
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(userRepository.existsByContactValue(anyString())).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        AuthResponse response = authenticationService.register(request);

        // Then
        assertNotNull(response);
        assertEquals(request.getUsername(), response.getUsername());
        assertEquals(request.getEmail(), response.getEmail());
        assertEquals("USER", response.getRole());
        assertFalse(response.isTwoFactorRequired());

        verify(userRepository).save(any(User.class));
        verify(notificationService).sendVerificationEmail(anyString(), any());
    }

    @Test
    void shouldThrowExceptionWhenUsernameExists() {
        // Given
        RegisterRequest request = RegisterRequest.builder()
            .username("existinguser")
            .firstName("John")
            .lastName("Doe")
            .email("test@example.com")
            .phone("+1234567890")
            .password("TestPassword123!")
            .build();

        when(userRepository.existsByUsername("existinguser")).thenReturn(true);

        // When & Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> authenticationService.register(request)
        );

        assertEquals("Username already exists", exception.getMessage());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void shouldThrowExceptionWhenEmailExists() {
        // Given
        RegisterRequest request = RegisterRequest.builder()
            .username("testuser")
            .firstName("John")
            .lastName("Doe")
            .email("existing@example.com")
            .phone("+1234567890")
            .password("TestPassword123!")
            .build();

        when(userRepository.existsByUsername(anyString())).thenReturn(false);
        when(userRepository.existsByEmail("existing@example.com")).thenReturn(true);

        // When & Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> authenticationService.register(request)
        );

        assertEquals("Email already exists", exception.getMessage());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void shouldThrowExceptionForInvalidCredentials() {
        // Given
        LoginRequest request = LoginRequest.builder()
            .contactValue("nonexistent@example.com")
            .password("password")
            .build();

        when(userRepository.findByContactValue("nonexistent@example.com")).thenReturn(Optional.empty());

        // When & Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> authenticationService.login(request, "127.0.0.1")
        );

        assertEquals("Invalid credentials", exception.getMessage());
    }
}
