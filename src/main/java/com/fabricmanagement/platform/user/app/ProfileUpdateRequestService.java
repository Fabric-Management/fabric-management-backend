package com.fabricmanagement.platform.user.app;

import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.platform.common.exception.PlatformDomainException;
import com.fabricmanagement.platform.user.domain.ProfileUpdateRequest;
import com.fabricmanagement.platform.user.domain.event.ProfileUpdateRequestApprovedEvent;
import com.fabricmanagement.platform.user.domain.event.ProfileUpdateRequestCreatedEvent;
import com.fabricmanagement.platform.user.domain.event.ProfileUpdateRequestRejectedEvent;
import com.fabricmanagement.platform.user.domain.value.ProfileUpdateRequestStatus;
import com.fabricmanagement.platform.user.dto.CreateProfileUpdateRequestDto;
import com.fabricmanagement.platform.user.dto.ProfileUpdateRequestDto;
import com.fabricmanagement.platform.user.dto.ReviewProfileUpdateRequestDto;
import com.fabricmanagement.platform.user.dto.UpdateUserProfileRequest;
import com.fabricmanagement.platform.user.infra.repository.ProfileUpdateRequestRepository;
import com.fabricmanagement.platform.user.infra.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing profile update requests.
 *
 * <p>Handles the workflow for users to request profile changes that require HR/Admin approval.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProfileUpdateRequestService {

  private final ProfileUpdateRequestRepository requestRepository;
  private final UserRepository userRepository;
  private final UserService userService;
  private final UserProfilePermissionService permissionService;
  private final DomainEventPublisher eventPublisher;
  private final ObjectMapper objectMapper;

  /**
   * Create a profile update request.
   *
   * @param userId The user requesting the update
   * @param requestDto The request details
   * @return Created request DTO
   */
  @Transactional
  public ProfileUpdateRequestDto createProfileUpdateRequest(
      UUID userId, CreateProfileUpdateRequestDto requestDto) {
    UUID tenantId = TenantContext.requireTenantId();
    log.info(
        "Creating profile update request: tenantId={}, userId={}, category={}",
        tenantId,
        userId,
        requestDto.getProfileCategory());

    // Verify user exists
    userRepository
        .findByTenantIdAndId(tenantId, userId)
        .orElseThrow(
            () ->
                new PlatformDomainException(
                    "User not found: " + userId, "USER_NOT_FOUND", 404, new Object[] {userId}));

    // Validate that user is requesting for themselves (self-update prevention at creation time)
    UUID currentUserId = TenantContext.getCurrentUserId();
    if (!userId.equals(currentUserId)) {
      throw new PlatformDomainException(
          "Users can only create requests for themselves",
          "USER_PROFILE_REQUEST_INVALID_OWNER",
          403);
    }

    // Convert requestedChanges to JSON string
    String requestedChangesJson = null;
    if (requestDto.getRequestedChanges() != null) {
      try {
        requestedChangesJson = objectMapper.writeValueAsString(requestDto.getRequestedChanges());
      } catch (Exception e) {
        log.error("Failed to serialize requestedChanges: {}", e.getMessage());
        throw new PlatformDomainException(
            "Invalid requestedChanges format", "USER_PROFILE_REQUEST_INVALID_FORMAT", 400);
      }
    }

    // Create request
    ProfileUpdateRequest request =
        ProfileUpdateRequest.builder()
            .userId(userId)
            .profileCategory(requestDto.getProfileCategory())
            .status(ProfileUpdateRequestStatus.PENDING)
            .requestedChanges(requestedChangesJson)
            .reason(requestDto.getReason())
            .build();

    ProfileUpdateRequest saved = requestRepository.save(request);

    // Publish event
    eventPublisher.publish(
        new ProfileUpdateRequestCreatedEvent(
            tenantId, saved.getId(), userId, requestDto.getProfileCategory().name()));

    log.info("✅ Profile update request created: requestId={}, userId={}", saved.getId(), userId);

    return ProfileUpdateRequestDto.from(saved);
  }

  /**
   * Approve a profile update request and apply changes.
   *
   * @param requestId The request ID
   * @param reviewerId The HR/Admin approving the request
   * @param reviewDto Review comments
   * @return Updated request DTO
   */
  @Transactional
  public ProfileUpdateRequestDto approveProfileUpdateRequest(
      UUID requestId, UUID reviewerId, ReviewProfileUpdateRequestDto reviewDto) {
    UUID tenantId = TenantContext.requireTenantId();
    log.info(
        "Approving profile update request: tenantId={}, requestId={}, reviewerId={}",
        tenantId,
        requestId,
        reviewerId);

    // Find request
    List<ProfileUpdateRequest> requests =
        requestRepository.findByTenantIdAndId(tenantId, requestId);
    if (requests.isEmpty()) {
      throw new PlatformDomainException(
          "Profile update request not found",
          "USER_PROFILE_REQUEST_NOT_FOUND",
          404,
          new Object[] {requestId});
    }

    ProfileUpdateRequest request = requests.get(0);

    // Validate request is pending
    if (!request.isPending()) {
      throw new PlatformDomainException(
          "Request is not pending",
          "USER_PROFILE_REQUEST_NOT_PENDING",
          400,
          new Object[] {request.getStatus()});
    }

    // Validate reviewer has permission
    if (!permissionService.canUpdateWorkProfile(reviewerId, request.getUserId())
        && !permissionService.canUpdatePersonalProfile(reviewerId, request.getUserId())) {
      throw new PlatformDomainException(
          "You don't have permission to approve this request",
          "USER_PROFILE_REQUEST_UNAUTHORIZED",
          403);
    }

    // Approve request
    request.approve(reviewerId, reviewDto.getReviewComment());
    ProfileUpdateRequest saved = requestRepository.save(request);

    // Apply changes to user profile
    try {
      applyProfileChanges(request);
    } catch (Exception e) {
      log.error("Failed to apply profile changes after approval: requestId={}", requestId, e);
      // Note: Request is already approved, but changes failed. This should trigger a
      // notification/alert.
      throw new PlatformDomainException(
          "Failed to apply profile changes",
          "USER_PROFILE_UPDATE_FAILED",
          500,
          new Object[] {e.getMessage()});
    }

    // Publish event
    eventPublisher.publish(
        new ProfileUpdateRequestApprovedEvent(
            tenantId,
            saved.getId(),
            request.getUserId(),
            reviewerId,
            request.getProfileCategory().name()));

    log.info(
        "✅ Profile update request approved: requestId={}, userId={}",
        saved.getId(),
        request.getUserId());

    return ProfileUpdateRequestDto.from(saved);
  }

  /**
   * Reject a profile update request.
   *
   * @param requestId The request ID
   * @param reviewerId The HR/Admin rejecting the request
   * @param reviewDto Review comments
   * @return Updated request DTO
   */
  @Transactional
  public ProfileUpdateRequestDto rejectProfileUpdateRequest(
      UUID requestId, UUID reviewerId, ReviewProfileUpdateRequestDto reviewDto) {
    UUID tenantId = TenantContext.requireTenantId();
    log.info(
        "Rejecting profile update request: tenantId={}, requestId={}, reviewerId={}",
        tenantId,
        requestId,
        reviewerId);

    // Find request
    List<ProfileUpdateRequest> requests =
        requestRepository.findByTenantIdAndId(tenantId, requestId);
    if (requests.isEmpty()) {
      throw new PlatformDomainException(
          "Profile update request not found",
          "USER_PROFILE_REQUEST_NOT_FOUND",
          404,
          new Object[] {requestId});
    }

    ProfileUpdateRequest request = requests.get(0);

    // Validate request is pending
    if (!request.isPending()) {
      throw new PlatformDomainException(
          "Request is not pending",
          "USER_PROFILE_REQUEST_NOT_PENDING",
          400,
          new Object[] {request.getStatus()});
    }

    // Validate reviewer has permission
    if (!permissionService.canUpdateWorkProfile(reviewerId, request.getUserId())
        && !permissionService.canUpdatePersonalProfile(reviewerId, request.getUserId())) {
      throw new PlatformDomainException(
          "You don't have permission to reject this request",
          "USER_PROFILE_REQUEST_UNAUTHORIZED",
          403);
    }

    // Reject request
    request.reject(reviewerId, reviewDto.getReviewComment());
    ProfileUpdateRequest saved = requestRepository.save(request);

    // Publish event
    eventPublisher.publish(
        new ProfileUpdateRequestRejectedEvent(
            tenantId,
            saved.getId(),
            request.getUserId(),
            reviewerId,
            request.getProfileCategory().name(),
            reviewDto.getReviewComment()));

    log.info(
        "✅ Profile update request rejected: requestId={}, userId={}",
        saved.getId(),
        request.getUserId());

    return ProfileUpdateRequestDto.from(saved);
  }

  /**
   * Get all profile update requests for a user.
   *
   * @param userId The user ID
   * @return List of requests
   */
  @Transactional(readOnly = true)
  public List<ProfileUpdateRequestDto> getMyProfileUpdateRequests(UUID userId) {
    UUID tenantId = TenantContext.requireTenantId();
    log.debug("Getting profile update requests: tenantId={}, userId={}", tenantId, userId);

    List<ProfileUpdateRequest> requests =
        requestRepository.findByTenantIdAndUserIdOrderByCreatedAtDesc(tenantId, userId);

    return requests.stream().map(ProfileUpdateRequestDto::from).toList();
  }

  /**
   * Get all pending profile update requests (for HR/Admin).
   *
   * @return List of pending requests
   */
  @Transactional(readOnly = true)
  public List<ProfileUpdateRequestDto> getPendingProfileUpdateRequests() {
    UUID tenantId = TenantContext.requireTenantId();
    log.debug("Getting pending profile update requests: tenantId={}", tenantId);

    List<ProfileUpdateRequest> requests =
        requestRepository.findByTenantIdAndStatusOrderByCreatedAtAsc(
            tenantId, ProfileUpdateRequestStatus.PENDING);

    return requests.stream().map(ProfileUpdateRequestDto::from).toList();
  }

  /**
   * Apply profile changes from approved request.
   *
   * @param request The approved request
   */
  private void applyProfileChanges(ProfileUpdateRequest request) {
    if (request.getRequestedChanges() == null || request.getRequestedChanges().isBlank()) {
      log.debug("No changes to apply: requestId={}", request.getId());
      return;
    }

    try {
      // Parse requested changes
      JsonNode changesJson = objectMapper.readTree(request.getRequestedChanges());

      // Convert to UpdateUserProfileRequest
      UpdateUserProfileRequest updateRequest =
          objectMapper.treeToValue(changesJson, UpdateUserProfileRequest.class);

      // Apply changes via UserService (which handles permissions and validation)
      userService.updateProfile(request.getUserId(), updateRequest, request.getReviewedBy());

      log.info(
          "✅ Profile changes applied: requestId={}, userId={}",
          request.getId(),
          request.getUserId());
    } catch (Exception e) {
      log.error("Failed to apply profile changes: requestId={}", request.getId(), e);
      throw new PlatformDomainException(
          "Failed to apply profile changes",
          "USER_PROFILE_UPDATE_FAILED",
          500,
          new Object[] {e.getMessage()});
    }
  }
}
