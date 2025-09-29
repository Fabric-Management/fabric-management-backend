package com.fabricmanagement.identity.application.port.in.command;

import com.fabricmanagement.identity.application.dto.auth.LoginRequest;
import com.fabricmanagement.identity.application.dto.auth.LoginResponse;
import com.fabricmanagement.identity.application.dto.auth.RefreshTokenRequest;
import com.fabricmanagement.identity.application.dto.auth.RefreshTokenResponse;
import com.fabricmanagement.identity.application.dto.auth.ChangePasswordRequest;
import com.fabricmanagement.identity.application.dto.auth.ForgotPasswordRequest;
import com.fabricmanagement.identity.application.dto.auth.ResetPasswordRequest;
import com.fabricmanagement.identity.application.dto.auth.TwoFactorRequest;
import com.fabricmanagement.identity.application.dto.auth.TwoFactorResponse;

/**
 * Single Responsibility: Authentication operations only
 * Interface Segregation: Only authentication-related methods
 */
public interface AuthenticationUseCase {
    
    /**
     * Authenticates a user with login credentials.
     */
    LoginResponse login(LoginRequest request);
    
    /**
     * Refreshes user authentication token.
     */
    RefreshTokenResponse refreshToken(RefreshTokenRequest request);
    
    /**
     * Logs out a user by invalidating their tokens.
     */
    void logout(String refreshToken);
    
    /**
     * Changes user password.
     */
    void changePassword(String userId, ChangePasswordRequest request);
    
    /**
     * Initiates password reset process.
     */
    void forgotPassword(ForgotPasswordRequest request);
    
    /**
     * Resets password using reset token.
     */
    void resetPassword(ResetPasswordRequest request);
    
    /**
     * Validates two-factor authentication code.
     */
    TwoFactorResponse validateTwoFactor(TwoFactorRequest request);
    
    /**
     * Enables two-factor authentication.
     */
    String enableTwoFactor(String userId);
    
    /**
     * Disables two-factor authentication.
     */
    void disableTwoFactor(String userId);
}