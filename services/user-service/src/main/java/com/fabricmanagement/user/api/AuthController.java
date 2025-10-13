package com.fabricmanagement.user.api;

import com.fabricmanagement.shared.application.response.ApiResponse;
import com.fabricmanagement.shared.infrastructure.constants.ServiceConstants;
import com.fabricmanagement.user.api.dto.request.CheckContactRequest;
import com.fabricmanagement.user.api.dto.request.LoginRequest;
import com.fabricmanagement.user.api.dto.request.SetupPasswordRequest;
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
}
