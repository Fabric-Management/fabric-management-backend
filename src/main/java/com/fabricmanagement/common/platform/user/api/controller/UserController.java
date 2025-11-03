package com.fabricmanagement.common.platform.user.api.controller;

import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.common.platform.communication.app.ContactSuggestionService;
import com.fabricmanagement.common.platform.communication.app.UserAddressService;
import com.fabricmanagement.common.platform.communication.app.UserContactService;
import com.fabricmanagement.common.platform.communication.dto.ContactSuggestionsDto;
import com.fabricmanagement.common.platform.communication.dto.UserAddressDto;
import com.fabricmanagement.common.platform.communication.dto.UserContactDto;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.platform.user.app.ProfileUpdateRequestService;
import com.fabricmanagement.common.platform.user.app.UserService;
import com.fabricmanagement.common.platform.user.dto.CreateProfileUpdateRequestDto;
import com.fabricmanagement.common.platform.user.dto.CreateUserRequest;
import com.fabricmanagement.common.platform.user.dto.OnboardingStatusResponse;
import com.fabricmanagement.common.platform.user.dto.ProfileUpdateRequestDto;
import com.fabricmanagement.common.platform.user.dto.ReviewProfileUpdateRequestDto;
import com.fabricmanagement.common.platform.user.dto.UpdateUserRequest;
import com.fabricmanagement.common.platform.user.dto.UpdateUserProfileRequest;
import com.fabricmanagement.common.platform.user.dto.UserDto;
import com.fabricmanagement.common.util.PiiMaskingUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/common/users")
@RequiredArgsConstructor
@Slf4j
public class UserController {

    private final UserService userService;
    private final ProfileUpdateRequestService profileUpdateRequestService;
    private final ContactSuggestionService contactSuggestionService;
    private final UserContactService userContactService;
    private final UserAddressService userAddressService;

    @PostMapping
    public ResponseEntity<ApiResponse<UserDto>> createUser(@Valid @RequestBody CreateUserRequest request) {
        log.info("Creating user: contactValue={}", 
            PiiMaskingUtil.maskEmail(request.getContactValue()));

        UserDto created = userService.createUser(request);

        return ResponseEntity.ok(ApiResponse.success(created, "User created successfully"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<UserDto>> getUser(@PathVariable UUID id) {
        log.debug("Getting user: id={}", id);

        UserDto user = userService.findById(
            com.fabricmanagement.common.infrastructure.persistence.TenantContext.getCurrentTenantId(), 
            id
        ).orElseThrow(() -> new IllegalArgumentException("User not found"));

        return ResponseEntity.ok(ApiResponse.success(user));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<UserDto>>> getAllUsers() {
        log.debug("Getting all users");

        List<UserDto> users = userService.findByTenant(
            com.fabricmanagement.common.infrastructure.persistence.TenantContext.getCurrentTenantId()
        );

        return ResponseEntity.ok(ApiResponse.success(users));
    }

    @GetMapping("/company/{companyId}")
    public ResponseEntity<ApiResponse<List<UserDto>>> getUsersByCompany(@PathVariable UUID companyId) {
        log.debug("Getting users by company: companyId={}", companyId);

        List<UserDto> users = userService.findByCompany(
            com.fabricmanagement.common.infrastructure.persistence.TenantContext.getCurrentTenantId(),
            companyId
        );

        return ResponseEntity.ok(ApiResponse.success(users));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<UserDto>> updateUser(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateUserRequest request) {
        log.info("Updating user: id={}", id);

        UserDto updated = userService.updateUser(id, request);

        return ResponseEntity.ok(ApiResponse.success(updated, "User updated successfully"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deactivateUser(@PathVariable UUID id) {
        log.info("Deactivating user: id={}", id);

        userService.deactivateUser(id, "Deactivated by admin");

        return ResponseEntity.ok(ApiResponse.success(null, "User deactivated successfully"));
    }

    @GetMapping("/contact/{contactValue}")
    public ResponseEntity<ApiResponse<Boolean>> checkContactExists(@PathVariable String contactValue) {
        log.debug("Checking contact existence: {}", 
            PiiMaskingUtil.maskEmail(contactValue));

        boolean exists = userService.contactExists(contactValue);

        return ResponseEntity.ok(ApiResponse.success(exists));
    }

    /**
     * Get current user profile (self-service endpoint).
     * 
     * <p>Uses TenantContext.getCurrentUserId() to get the authenticated user.</p>
     */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserDto>> getCurrentUser() {
        UUID userId = com.fabricmanagement.common.infrastructure.persistence.TenantContext.getCurrentUserId();
        
        if (userId == null) {
            throw new IllegalStateException("User not authenticated");
        }

        log.debug("Getting current user profile: userId={}", userId);

        UserDto user = userService.findById(
            com.fabricmanagement.common.infrastructure.persistence.TenantContext.getCurrentTenantId(),
            userId
        ).orElseThrow(() -> new IllegalArgumentException("User not found"));

        return ResponseEntity.ok(ApiResponse.success(user));
    }

    /**
     * Get onboarding status for current user.
     */
    @GetMapping("/me/onboarding-status")
    public ResponseEntity<ApiResponse<OnboardingStatusResponse>> getOnboardingStatus() {
        UUID userId = com.fabricmanagement.common.infrastructure.persistence.TenantContext.getCurrentUserId();
        
        if (userId == null) {
            throw new IllegalStateException("User not authenticated");
        }

        log.debug("Getting onboarding status: userId={}", userId);

        boolean completed = userService.hasCompletedOnboarding(userId);

        return ResponseEntity.ok(ApiResponse.success(
            OnboardingStatusResponse.builder()
                .hasCompletedOnboarding(completed)
                .build()
        ));
    }

    /**
     * Complete onboarding for current user.
     */
    @PostMapping("/me/onboarding/complete")
    public ResponseEntity<ApiResponse<UserDto>> completeOnboarding() {
        UUID userId = com.fabricmanagement.common.infrastructure.persistence.TenantContext.getCurrentUserId();
        
        if (userId == null) {
            throw new IllegalStateException("User not authenticated");
        }

        log.info("Completing onboarding: userId={}", userId);

        UserDto user = userService.completeOnboarding(userId);

        return ResponseEntity.ok(ApiResponse.success(user, "Onboarding completed successfully"));
    }

    /**
     * Update user profile (Admin/HR/Dept Manager only).
     * 
     * <p><b>Security:</b> Self-update is NOT allowed. Only authorized users can update profiles.</p>
     * <p>Permission checks:
     * <ul>
     *   <li>WORK_PROFILE: Admin, HR Manager, or Department Manager (same department)</li>
     *   <li>PERSONAL_PROFILE: Only Admin or HR Manager</li>
     * </ul>
     */
    @PutMapping("/{id}/profile")
    public ResponseEntity<ApiResponse<UserDto>> updateUserProfile(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateUserProfileRequest request) {
        
        UUID requesterId = com.fabricmanagement.common.infrastructure.persistence.TenantContext.getCurrentUserId();
        
        if (requesterId == null) {
            throw new IllegalStateException("User not authenticated");
        }

        log.info("Profile update request: targetUserId={}, requesterId={}", id, requesterId);
        
        UserDto updated = userService.updateProfile(id, request, requesterId);
        
        return ResponseEntity.ok(
            ApiResponse.success(updated, "User profile updated successfully")
        );
    }

    /**
     * Get contact suggestions for user creation.
     *
     * <p>Returns intelligent suggestions based on company contacts:</p>
     * <ul>
     *   <li>Phone suggestion from company default phone</li>
     *   <li>Email suggestions from company domain (multiple formats)</li>
     * </ul>
     *
     * <p><b>Purpose:</b> Minimize manual data entry during user creation.</p>
     *
     * @param companyId Company ID
     * @param firstName User first name
     * @param lastName User last name
     * @return Contact suggestions
     */
    @GetMapping("/contact-suggestions")
    public ResponseEntity<ApiResponse<ContactSuggestionsDto>> getContactSuggestions(
            @RequestParam UUID companyId,
            @RequestParam String firstName,
            @RequestParam String lastName) {
        log.debug("Getting contact suggestions: companyId={}, firstName={}, lastName={}", 
            companyId, firstName, lastName);

        ContactSuggestionsDto suggestions = contactSuggestionService.getSuggestions(
            companyId, firstName, lastName
        );

        return ResponseEntity.ok(ApiResponse.success(suggestions));
    }

    /**
     * Get user contacts.
     *
     * <p>Returns all contacts assigned to the user, including:</p>
     * <ul>
     *   <li>Contact details (value, type, verification status)</li>
     *   <li>Default contact flag</li>
     *   <li>Authentication contact flag</li>
     * </ul>
     *
     * @param id User ID
     * @return List of user contacts
     */
    @GetMapping("/{id}/contacts")
    public ResponseEntity<ApiResponse<List<UserContactDto>>> getUserContacts(@PathVariable UUID id) {
        log.debug("Getting user contacts: userId={}", id);

        // Validate user exists (tenant-scoped)
        userService.findById(
            com.fabricmanagement.common.infrastructure.persistence.TenantContext.getCurrentTenantId(),
            id
        ).orElseThrow(() -> new IllegalArgumentException("User not found"));

        List<UserContactDto> contacts = userContactService.getUserContacts(id).stream()
            .map(UserContactDto::from)
            .toList();

        return ResponseEntity.ok(ApiResponse.success(contacts));
    }

    /**
     * Get user addresses.
     *
     * <p>Returns all addresses assigned to the user, including:</p>
     * <ul>
     *   <li>Address details (full address, coordinates, type)</li>
     *   <li>Primary address flag</li>
     *   <li>Work address flag</li>
     * </ul>
     *
     * @param id User ID
     * @return List of user addresses
     */
    @GetMapping("/{id}/addresses")
    public ResponseEntity<ApiResponse<List<UserAddressDto>>> getUserAddresses(@PathVariable UUID id) {
        log.debug("Getting user addresses: userId={}", id);

        // Validate user exists (tenant-scoped)
        userService.findById(
            com.fabricmanagement.common.infrastructure.persistence.TenantContext.getCurrentTenantId(),
            id
        ).orElseThrow(() -> new IllegalArgumentException("User not found"));

        List<UserAddressDto> addresses = userAddressService.getUserAddresses(id).stream()
            .map(UserAddressDto::from)
            .toList();

        return ResponseEntity.ok(ApiResponse.success(addresses));
    }

    // =========================================================================
    // PROFILE UPDATE REQUEST ENDPOINTS (Phase 2)
    // =========================================================================

    /**
     * Create a profile update request.
     * User can request profile changes that require HR/Admin approval.
     */
    @PostMapping("/me/profile/update-request")
    public ResponseEntity<ApiResponse<ProfileUpdateRequestDto>> createProfileUpdateRequest(
            @Valid @RequestBody CreateProfileUpdateRequestDto request) {
        UUID userId = TenantContext.getCurrentUserId();
        
        if (userId == null) {
            throw new IllegalStateException("User not authenticated");
        }

        log.info("Creating profile update request: userId={}, category={}", 
            userId, request.getProfileCategory());

        ProfileUpdateRequestDto created = profileUpdateRequestService
            .createProfileUpdateRequest(userId, request);

        return ResponseEntity.ok(ApiResponse.success(created, 
            "Profile update request submitted. HR will review and update."));
    }

    /**
     * Get all profile update requests for the current user.
     */
    @GetMapping("/me/profile/update-requests")
    public ResponseEntity<ApiResponse<List<ProfileUpdateRequestDto>>> getMyProfileUpdateRequests() {
        UUID userId = TenantContext.getCurrentUserId();
        
        if (userId == null) {
            throw new IllegalStateException("User not authenticated");
        }

        log.debug("Getting profile update requests: userId={}", userId);

        List<ProfileUpdateRequestDto> requests = profileUpdateRequestService
            .getMyProfileUpdateRequests(userId);

        return ResponseEntity.ok(ApiResponse.success(requests));
    }

    /**
     * Get all pending profile update requests (for HR/Admin).
     */
    @GetMapping("/profile/update-requests/pending")
    public ResponseEntity<ApiResponse<List<ProfileUpdateRequestDto>>> getPendingProfileUpdateRequests() {
        log.debug("Getting pending profile update requests");

        List<ProfileUpdateRequestDto> requests = profileUpdateRequestService
            .getPendingProfileUpdateRequests();

        return ResponseEntity.ok(ApiResponse.success(requests));
    }

    /**
     * Approve a profile update request.
     * HR/Admin can approve requests and the changes will be applied automatically.
     */
    @PutMapping("/{id}/profile/update-requests/{requestId}/approve")
    public ResponseEntity<ApiResponse<ProfileUpdateRequestDto>> approveProfileUpdateRequest(
            @PathVariable UUID id,
            @PathVariable UUID requestId,
            @Valid @RequestBody ReviewProfileUpdateRequestDto reviewDto) {
        UUID reviewerId = TenantContext.getCurrentUserId();
        
        if (reviewerId == null) {
            throw new IllegalStateException("User not authenticated");
        }

        log.info("Approving profile update request: userId={}, requestId={}, reviewerId={}", 
            id, requestId, reviewerId);

        ProfileUpdateRequestDto approved = profileUpdateRequestService
            .approveProfileUpdateRequest(requestId, reviewerId, reviewDto);

        return ResponseEntity.ok(ApiResponse.success(approved, 
            "Profile update request approved and changes applied successfully."));
    }

    /**
     * Reject a profile update request.
     * HR/Admin can reject requests with a comment.
     */
    @PutMapping("/{id}/profile/update-requests/{requestId}/reject")
    public ResponseEntity<ApiResponse<ProfileUpdateRequestDto>> rejectProfileUpdateRequest(
            @PathVariable UUID id,
            @PathVariable UUID requestId,
            @Valid @RequestBody ReviewProfileUpdateRequestDto reviewDto) {
        UUID reviewerId = TenantContext.getCurrentUserId();
        
        if (reviewerId == null) {
            throw new IllegalStateException("User not authenticated");
        }

        log.info("Rejecting profile update request: userId={}, requestId={}, reviewerId={}", 
            id, requestId, reviewerId);

        ProfileUpdateRequestDto rejected = profileUpdateRequestService
            .rejectProfileUpdateRequest(requestId, reviewerId, reviewDto);

        return ResponseEntity.ok(ApiResponse.success(rejected, 
            "Profile update request rejected."));
    }
}

