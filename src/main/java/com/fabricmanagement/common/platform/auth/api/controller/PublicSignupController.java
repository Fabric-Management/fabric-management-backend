package com.fabricmanagement.common.platform.auth.api.controller;

import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.common.platform.auth.app.TenantOnboardingService;
import com.fabricmanagement.common.platform.auth.dto.SelfSignupRequest;
import com.fabricmanagement.common.platform.auth.dto.TenantOnboardingResponse;
import com.fabricmanagement.common.platform.communication.app.NotificationService;
import com.fabricmanagement.common.util.PiiMaskingUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public Signup Controller - Self-service tenant registration.
 *
 * <p><b>Public endpoints</b> - No authentication required</p>
 *
 * <p>Used by users signing up from the website.</p>
 *
 * <h2>Flow:</h2>
 * <pre>
 * 1. POST /api/public/signup
 *    → Creates tenant + company + admin user
 *    → Sends email with token + verification code
 *
 * 2. User clicks email link
 *    → Redirects to /setup?token=xyz
 *
 * 3. User enters verification code + password
 *    → POST /api/auth/setup-password
 *    → Auto-login → Dashboard (onboarding wizard if needed)
 * </pre>
 */
@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
@Slf4j
public class PublicSignupController {

    private final TenantOnboardingService onboardingService;
    private final NotificationService notificationService;

    /**
     * Self-service signup - Creates new tenant from public website.
     *
     * @param request Self signup request
     * @return Onboarding response (without sensitive data)
     */
    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<String>> signup(@Valid @RequestBody SelfSignupRequest request) {
        
        log.info("Public signup request: company={}, email={}",
            request.getCompanyName(),
            PiiMaskingUtil.maskEmail(request.getEmail()));

        TenantOnboardingResponse response = onboardingService.createSelfServiceTenant(request);

        sendSelfServiceWelcomeEmail(
            request.getEmail(),
            request.getFirstName(),
            response.getCompanyName(),
            response.getRegistrationToken()
        );

        log.info("✅ Self-service signup completed: companyUid={}, email sent with setup link",
            response.getCompanyUid());

        return ResponseEntity.ok(ApiResponse.success(
            "Welcome! Check your email to complete registration.",
            "Registration initiated successfully"
        ));
    }

    /**
     * Send self-service welcome email with setup link.
     */
    private void sendSelfServiceWelcomeEmail(String email, String firstName, String companyName,
                                            String token) {
        String setupUrl = String.format("http://localhost:3000/setup?token=%s", token);
        
        String subject = "Complete Your FabricOS Registration";
        
        String message = String.format("""
            Hello %s!
            
            Thank you for signing up to FabricOS!
            Your account for %s is ready to activate.
            
            Trial Period: 14 days FREE - No credit card required!
            
            Complete your registration:
            %s
            
            This link will expire in 24 hours.
            
            Need help? Contact support@fabricmanagement.com
            
            Best regards,
            FabricOS Team
            """, firstName, companyName, setupUrl);

        notificationService.sendNotification(email, subject, message);
    }
}

