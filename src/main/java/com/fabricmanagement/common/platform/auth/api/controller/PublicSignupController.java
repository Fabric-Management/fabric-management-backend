package com.fabricmanagement.common.platform.auth.api.controller;

import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.common.platform.auth.app.TenantOnboardingService;
import com.fabricmanagement.common.platform.auth.dto.SelfSignupRequest;
import com.fabricmanagement.common.platform.auth.dto.TenantOnboardingResponse;
import com.fabricmanagement.common.platform.communication.app.EmailTemplateRenderer;
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
     *    → Sends email with setup link (token only)
     *
     * 2. User clicks email link
     *    → Redirects to /setup?token=xyz
     *    → Email verified by click (no verification code needed)
     *
     * 3. User sets password
     *    → POST /api/auth/setup-password (token + password only)
     *    → Auto-login → Dashboard (onboarding wizard if needed)
     * </pre>
     *
     * <p><b>Note:</b> Verification codes are NOT used in self-service signup.
     * Email link click = email verified. Verification codes are only used
     * for unverified contacts during login flows.</p>
     */
@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
@Slf4j
public class PublicSignupController {

    private final TenantOnboardingService onboardingService;
    private final NotificationService notificationService;
    private final EmailTemplateRenderer emailTemplateRenderer;

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
     * 
     * <p>Note: No verification code needed - email link click is sufficient verification.
     * Verification codes are only used for unverified contacts during login flows.</p>
     */
    private void sendSelfServiceWelcomeEmail(String email, String firstName, String companyName, String token) {
        String setupUrl = generateSetupUrl(token);
        String subject = "Complete Your FabricOS Registration";
        
        // Smart renderer: Uses frontend templates with backend fallback
        // Frontend templates prioritized for better UX (design system consistency)
        String message = emailTemplateRenderer.renderSetupPassword(firstName, companyName, email, setupUrl);
        
        // Async email sending - don't block user response
        notificationService.sendNotification(email, subject, message);
    }
    
    /**
     * Generate setup URL using configured frontend URL.
     */
    private String generateSetupUrl(String token) {
        String baseUrl = System.getenv("FRONTEND_URL");
        if (baseUrl == null || baseUrl.isEmpty()) {
            baseUrl = System.getenv("APP_BASE_URL");
        }
        if (baseUrl == null || baseUrl.isEmpty()) {
            // Fallback for local development only
            baseUrl = "http://localhost:3000";
            log.warn("⚠️ Using hardcoded frontend URL for setup link. Set FRONTEND_URL or APP_BASE_URL env var.");
        }
        return baseUrl + "/setup?token=" + token;
    }    
}

