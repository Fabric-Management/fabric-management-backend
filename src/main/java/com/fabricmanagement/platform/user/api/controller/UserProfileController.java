package com.fabricmanagement.platform.user.api.controller;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.security.PermissionEvaluator;
import com.fabricmanagement.common.infrastructure.security.dto.PermissionResult;
import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.platform.common.exception.PlatformDomainException;
import com.fabricmanagement.platform.user.app.ProfileUpdateRequestService;
import com.fabricmanagement.platform.user.app.UserService;
import com.fabricmanagement.platform.user.dto.CreateProfileUpdateRequestDto;
import com.fabricmanagement.platform.user.dto.ProfileUpdateRequestDto;
import com.fabricmanagement.platform.user.dto.ReviewProfileUpdateRequestDto;
import com.fabricmanagement.platform.user.dto.UpdateUserProfileRequest;
import com.fabricmanagement.platform.user.dto.UserDto;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

/**
 * Profile update and profile-update-request endpoints.
 *
 * <p>Base path: /api/common/users — profile and approval workflow.
 */
@RestController
@RequestMapping("/api/common/users")
@RequiredArgsConstructor
@Slf4j
public class UserProfileController {

  private final UserService userService;
  private final ProfileUpdateRequestService profileUpdateRequestService;
  private final PermissionEvaluator permissionEvaluator;

  private PermissionResult getPermissions(UUID requesterId) {
    UUID tenantId = TenantContext.requireTenantId();
    UserDto requester =
        userService
            .findByIdWithPermissionData(tenantId, requesterId)
            .orElseThrow(() -> new NotFoundException("User not found"));
    return permissionEvaluator.evaluate(
        tenantId, requester.getRoleCode(), requester.getDepartmentCodes(), requesterId);
  }

  /**
   * Update user profile (Admin/HR/Dept Manager only).
   *
   * <p><b>Security:</b> Self-update is NOT allowed. Only authorized users can update profiles.
   *
   * <p>Permission checks:
   *
   * <ul>
   *   <li>WORK_PROFILE: Admin, HR Manager, or Department Manager (same department)
   *   <li>PERSONAL_PROFILE: Only Admin or HR Manager
   * </ul>
   */
  @PutMapping("/{id}/profile")
  public ResponseEntity<ApiResponse<UserDto>> updateUserProfile(
      @PathVariable UUID id, @Valid @RequestBody UpdateUserProfileRequest request) {

    UUID requesterId = TenantContext.getCurrentUserId();

    if (requesterId == null) {
      throw new PlatformDomainException("User not authenticated", "USER_NOT_AUTHENTICATED", 401);
    }

    if (!getPermissions(requesterId).can("members", "write")) {
      throw new AccessDeniedException(
          "Only Administration Office and Human Resources can update member profiles.");
    }

    log.info("Profile update request: targetUserId={}, requesterId={}", id, requesterId);

    UserDto updated = userService.updateProfile(id, request, requesterId);

    return ResponseEntity.ok(ApiResponse.success(updated, "User profile updated successfully"));
  }

  /**
   * Create a profile update request. User can request profile changes that require HR/Admin
   * approval.
   */
  @PostMapping("/me/profile/update-request")
  public ResponseEntity<ApiResponse<ProfileUpdateRequestDto>> createProfileUpdateRequest(
      @Valid @RequestBody CreateProfileUpdateRequestDto request) {
    UUID userId = TenantContext.getCurrentUserId();

    if (userId == null) {
      throw new PlatformDomainException("User not authenticated", "USER_NOT_AUTHENTICATED", 401);
    }

    log.info(
        "Creating profile update request: userId={}, category={}",
        userId,
        request.getProfileCategory());

    ProfileUpdateRequestDto created =
        profileUpdateRequestService.createProfileUpdateRequest(userId, request);

    return ResponseEntity.ok(
        ApiResponse.success(
            created, "Profile update request submitted. HR will review and update."));
  }

  /** Get all profile update requests for the current user. */
  @GetMapping("/me/profile/update-requests")
  public ResponseEntity<ApiResponse<List<ProfileUpdateRequestDto>>> getMyProfileUpdateRequests() {
    UUID userId = TenantContext.getCurrentUserId();

    if (userId == null) {
      throw new PlatformDomainException("User not authenticated", "USER_NOT_AUTHENTICATED", 401);
    }

    log.debug("Getting profile update requests: userId={}", userId);

    List<ProfileUpdateRequestDto> requests =
        profileUpdateRequestService.getMyProfileUpdateRequests(userId);

    return ResponseEntity.ok(ApiResponse.success(requests));
  }

  /** Get all pending profile update requests (for HR/Admin). */
  @GetMapping("/profile/update-requests/pending")
  public ResponseEntity<ApiResponse<List<ProfileUpdateRequestDto>>>
      getPendingProfileUpdateRequests() {
    log.debug("Getting pending profile update requests");

    List<ProfileUpdateRequestDto> requests =
        profileUpdateRequestService.getPendingProfileUpdateRequests();

    return ResponseEntity.ok(ApiResponse.success(requests));
  }

  /**
   * Approve a profile update request. HR/Admin can approve requests and the changes will be applied
   * automatically.
   */
  @PutMapping("/{id}/profile/update-requests/{requestId}/approve")
  public ResponseEntity<ApiResponse<ProfileUpdateRequestDto>> approveProfileUpdateRequest(
      @PathVariable UUID id,
      @PathVariable UUID requestId,
      @Valid @RequestBody ReviewProfileUpdateRequestDto reviewDto) {
    UUID reviewerId = TenantContext.getCurrentUserId();

    if (reviewerId == null) {
      throw new PlatformDomainException("User not authenticated", "USER_NOT_AUTHENTICATED", 401);
    }

    log.info(
        "Approving profile update request: userId={}, requestId={}, reviewerId={}",
        id,
        requestId,
        reviewerId);

    ProfileUpdateRequestDto approved =
        profileUpdateRequestService.approveProfileUpdateRequest(requestId, reviewerId, reviewDto);

    return ResponseEntity.ok(
        ApiResponse.success(
            approved, "Profile update request approved and changes applied successfully."));
  }

  /** Reject a profile update request. HR/Admin can reject requests with a comment. */
  @PutMapping("/{id}/profile/update-requests/{requestId}/reject")
  public ResponseEntity<ApiResponse<ProfileUpdateRequestDto>> rejectProfileUpdateRequest(
      @PathVariable UUID id,
      @PathVariable UUID requestId,
      @Valid @RequestBody ReviewProfileUpdateRequestDto reviewDto) {
    UUID reviewerId = TenantContext.getCurrentUserId();

    if (reviewerId == null) {
      throw new PlatformDomainException("User not authenticated", "USER_NOT_AUTHENTICATED", 401);
    }

    log.info(
        "Rejecting profile update request: userId={}, requestId={}, reviewerId={}",
        id,
        requestId,
        reviewerId);

    ProfileUpdateRequestDto rejected =
        profileUpdateRequestService.rejectProfileUpdateRequest(requestId, reviewerId, reviewDto);

    return ResponseEntity.ok(ApiResponse.success(rejected, "Profile update request rejected."));
  }
}
