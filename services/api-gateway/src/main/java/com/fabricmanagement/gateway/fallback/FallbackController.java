package com.fabricmanagement.gateway.fallback;

import com.fabricmanagement.shared.application.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Fallback Controller
 *
 * Provides fallback responses when downstream services are unavailable.
 * Triggered by Circuit Breaker when a service fails.
 */
@RestController
@RequestMapping("/fallback")
@Slf4j
public class FallbackController {

    @GetMapping("/user-service")
    public ResponseEntity<ApiResponse<Void>> userServiceFallback() {
        log.error("User Service is unavailable - Circuit breaker triggered");
        return ResponseEntity
            .status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(ApiResponse.error(
                "User Service is temporarily unavailable. Please try again later.",
                "SERVICE_UNAVAILABLE"
            ));
    }

    @GetMapping("/company-service")
    public ResponseEntity<ApiResponse<Void>> companyServiceFallback() {
        log.error("Company Service is unavailable - Circuit breaker triggered");
        return ResponseEntity
            .status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(ApiResponse.error(
                "Company Service is temporarily unavailable. Please try again later.",
                "SERVICE_UNAVAILABLE"
            ));
    }

    @GetMapping("/contact-service")
    public ResponseEntity<ApiResponse<Void>> contactServiceFallback() {
        log.error("Contact Service is unavailable - Circuit breaker triggered");
        return ResponseEntity
            .status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(ApiResponse.error(
                "Contact Service is temporarily unavailable. Please try again later.",
                "SERVICE_UNAVAILABLE"
            ));
    }
}
