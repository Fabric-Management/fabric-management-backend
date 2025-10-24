package com.fabricmanagement.user.api;

import com.fabricmanagement.shared.application.response.ApiResponse;
import com.fabricmanagement.shared.infrastructure.constants.ServiceConstants;
import com.fabricmanagement.user.api.dto.request.CheckContactRequest;
import com.fabricmanagement.user.api.dto.request.LoginRequest;
import com.fabricmanagement.user.api.dto.request.SetupPasswordRequest;
import com.fabricmanagement.user.api.dto.request.SetupPasswordWithVerificationRequest;
import com.fabricmanagement.user.api.dto.request.SendVerificationRequest;
import com.fabricmanagement.user.api.dto.response.CheckContactResponse;
import com.fabricmanagement.user.api.dto.response.LoginResponse;
import com.fabricmanagement.user.application.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final AuthService authService;

    @PostMapping("/check-contact")
    public ResponseEntity<ApiResponse<CheckContactResponse>> checkContact(
            @Valid @RequestBody CheckContactRequest request) {

        log.debug("Checking contact: {}", request.getContactValue());
        CheckContactResponse response = authService.checkContact(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/setup-password")
    public ResponseEntity<ApiResponse<Void>> setupPassword(
            @Valid @RequestBody SetupPasswordRequest request) {

        log.info("Setting up password for contact: {}", request.getContactValue());
        authService.setupPassword(request);
        return ResponseEntity.ok(ApiResponse.success(null, ServiceConstants.MSG_PASSWORD_CREATED));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(
            @Valid @RequestBody LoginRequest request) {

        log.info("Login attempt for contact: {}", request.getContactValue());
        LoginResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.success(response, "Login successful"));
    }
    
    @PostMapping("/send-verification")
    public ResponseEntity<ApiResponse<Void>> sendVerification(
            @Valid @RequestBody SendVerificationRequest request) {
        
        log.info("Sending verification code to: {}", request.getContactValue());
        authService.sendVerificationCode(request);
        return ResponseEntity.ok(ApiResponse.success(null, "Verification code sent successfully"));
    }
    
    @PostMapping("/setup-password-with-verification")
    public ResponseEntity<ApiResponse<LoginResponse>> setupPasswordWithVerification(
            @Valid @RequestBody SetupPasswordWithVerificationRequest request) {
        
        log.info("Setup password with verification for: {}", request.getContactValue());
        LoginResponse response = authService.setupPasswordWithVerification(request);
        return ResponseEntity.ok(ApiResponse.success(response, "Password created and logged in successfully"));
    }
}
