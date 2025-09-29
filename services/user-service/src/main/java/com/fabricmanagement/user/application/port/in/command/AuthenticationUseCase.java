package com.fabricmanagement.user.application.port.in.command;

import com.fabricmanagement.user.application.dto.auth.request.LoginRequest;
import com.fabricmanagement.user.application.dto.auth.response.AuthResponse;

/**
 * Port interface for user authentication.
 * This follows Hexagonal Architecture principles as an inbound port.
 */
public interface AuthenticationUseCase {
    
    /**
     * Authenticates a user with login credentials.
     *
     * @param request the login request
     * @return the authentication response
     */
    AuthResponse login(LoginRequest request);
    
    /**
     * Refreshes user authentication token.
     *
     * @param refreshToken the refresh token
     * @return the new authentication response
     */
    AuthResponse refreshToken(String refreshToken);
}
