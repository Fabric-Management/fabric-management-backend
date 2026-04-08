package com.fabricmanagement.platform.user.api.controller;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.security.PermissionEvaluator;
import com.fabricmanagement.common.infrastructure.security.dto.PermissionResult;
import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.common.infrastructure.web.rate.RateLimited;
import com.fabricmanagement.common.util.PiiMaskingUtil;
import com.fabricmanagement.platform.common.exception.PlatformDomainException;
import com.fabricmanagement.platform.communication.app.ContactSuggestionService;
import com.fabricmanagement.platform.communication.dto.ContactSuggestionsDto;
import com.fabricmanagement.platform.subscription.app.UserCreationOptionsService;
import com.fabricmanagement.platform.user.app.TeamAccessService;
import com.fabricmanagement.platform.user.app.UserLocaleService;
import com.fabricmanagement.platform.user.app.UserService;
import com.fabricmanagement.platform.user.domain.port.EmployeeCreationPort;
import com.fabricmanagement.platform.user.dto.CreateExternalUserRequest;
import com.fabricmanagement.platform.user.dto.CreateInternalUserRequest;
import com.fabricmanagement.platform.user.dto.UpdateLocalePreferencesRequest;
import com.fabricmanagement.platform.user.dto.UpdateUserRequest;
import com.fabricmanagement.platform.user.dto.UserDto;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/common/users")
@RequiredArgsConstructor
@Slf4j
public class UserController {

  private final UserService userService;
  private final ContactSuggestionService contactSuggestionService;
  private final EmployeeCreationPort employeeCreationPort;
  private final UserCreationOptionsService userCreationOptionsService;
  private final TeamAccessService teamAccessService;
  private final UserLocaleService userLocaleService;
  private final PermissionEvaluator permissionEvaluator;

  /**
   * Create internal employee (own staff with HR data).
   *
   * <p><b>Use Case:</b> Creating employees for your own company (tenant users with HR records).
   *
   * <p><b>Includes:</b> Title, gender, birth date, nationality, emergency contact, employee number,
   * hire date.
   */
  @PreAuthorize("isAuthenticated()")
  @PostMapping("/internal")
  public ResponseEntity<ApiResponse<UserDto>> createInternalUser(
      @Valid @RequestBody CreateInternalUserRequest request) {
    UUID requesterId = TenantContext.getCurrentUserId();
    if (!teamAccessService.canManageMembers(requesterId)) {
      throw new AccessDeniedException(
          "Only Administration Office and Human Resources can create members.");
    }

    log.info(
        "Creating internal employee: contactValue={}, requesterId={}",
        PiiMaskingUtil.maskEmail(request.getContactValue()),
        requesterId);

    UserDto created = userService.createInternalUser(request);

    return ResponseEntity.ok(
        ApiResponse.success(created, "Internal employee created successfully"));
  }

  /**
   * Create external user (partner/supplier/customer users without HR data).
   *
   * <p><b>Use Case:</b> Creating users for partner companies, suppliers, or customers (no HR
   * records needed).
   *
   * <p><b>Includes:</b> Only basic user information (no HR data).
   */
  @PreAuthorize("isAuthenticated()")
  @PostMapping("/external")
  public ResponseEntity<ApiResponse<UserDto>> createExternalUser(
      @Valid @RequestBody CreateExternalUserRequest request) {
    UUID requesterId = TenantContext.getCurrentUserId();
    if (!teamAccessService.canManageMembers(requesterId)) {
      throw new AccessDeniedException(
          "Only Administration Office and Human Resources can create members.");
    }

    log.info(
        "Creating external user: contactValue={}, requesterId={}",
        PiiMaskingUtil.maskEmail(request.getContactValue()),
        requesterId);

    UserDto created = userService.createExternalUser(request);

    return ResponseEntity.ok(ApiResponse.success(created, "External user created successfully"));
  }

  @PreAuthorize("isAuthenticated()")
  @GetMapping("/{id}")
  public ResponseEntity<ApiResponse<UserDto>> getUser(@PathVariable UUID id) {
    UUID requesterId = TenantContext.getCurrentUserId();
    if (!teamAccessService.canViewDepartmentMembers(requesterId)) {
      throw new AccessDeniedException("You don't have permission to view member details.");
    }

    log.debug("Getting user: id={}, requesterId={}", id, requesterId);

    UserDto user =
        userService
            .findById(TenantContext.getCurrentTenantId(), id)
            .orElseThrow(() -> new NotFoundException("User not found: " + id));

    return ResponseEntity.ok(ApiResponse.success(user));
  }

  /**
   * Get users for current tenant, scoped by requester's access level.
   *
   * <ul>
   *   <li>FULL_ACCESS / READ_ALL → all active users (cached per tenant)
   *   <li>DEPARTMENT_ONLY → users in requester's departments only
   *   <li>NO_ACCESS → 403
   * </ul>
   */
  @PreAuthorize("isAuthenticated()")
  @GetMapping
  public ResponseEntity<ApiResponse<List<UserDto>>> getAllUsers() {
    UUID requesterId = TenantContext.getCurrentUserId();
    UUID tenantId = TenantContext.getCurrentTenantId();

    TeamAccessService.AccessLevel accessLevel = teamAccessService.resolveAccessLevel(requesterId);

    if (accessLevel == TeamAccessService.AccessLevel.NO_ACCESS) {
      throw new AccessDeniedException("You don't have permission to view team members.");
    }

    if (accessLevel == TeamAccessService.AccessLevel.DEPARTMENT_ONLY) {
      log.debug("Getting department-scoped users: requesterId={}", requesterId);
      Set<UUID> deptIds = teamAccessService.getUserDepartmentIds(requesterId);
      List<UserDto> users = userService.findByDepartments(tenantId, deptIds);
      return ResponseEntity.ok(ApiResponse.success(users));
    }

    log.debug("Getting all users: requesterId={}, accessLevel={}", requesterId, accessLevel);
    List<UserDto> users = userService.findByTenant(tenantId);
    return ResponseEntity.ok(ApiResponse.success(users));
  }

  @PreAuthorize("isAuthenticated()")
  @GetMapping("/organization/{organizationId}")
  public ResponseEntity<ApiResponse<List<UserDto>>> getUsersByOrganization(
      @PathVariable UUID organizationId) {
    UUID requesterId = TenantContext.getCurrentUserId();
    if (!teamAccessService.canViewAllMembers(requesterId)) {
      throw new AccessDeniedException("You don't have permission to view organization members.");
    }

    log.debug(
        "Getting users by organization: organizationId={}, requesterId={}",
        organizationId,
        requesterId);

    List<UserDto> users =
        userService.findByOrganization(TenantContext.getCurrentTenantId(), organizationId);

    return ResponseEntity.ok(ApiResponse.success(users));
  }

  @PreAuthorize("isAuthenticated()")
  @PutMapping("/{id}")
  public ResponseEntity<ApiResponse<UserDto>> updateUser(
      @PathVariable UUID id, @Valid @RequestBody UpdateUserRequest request) {
    UUID requesterId = TenantContext.getCurrentUserId();
    if (!teamAccessService.canManageMembers(requesterId)) {
      throw new AccessDeniedException(
          "Only Administration Office and Human Resources can edit members.");
    }

    log.info("Updating user: id={}, requesterId={}", id, requesterId);

    UserDto updated = userService.updateUser(id, request);

    return ResponseEntity.ok(ApiResponse.success(updated, "User updated successfully"));
  }

  @PreAuthorize("isAuthenticated()")
  @DeleteMapping("/{id}")
  public ResponseEntity<ApiResponse<Void>> deactivateUser(@PathVariable UUID id) {
    UUID requesterId = TenantContext.getCurrentUserId();
    if (!teamAccessService.canManageMembers(requesterId)) {
      throw new AccessDeniedException(
          "Only Administration Office and Human Resources can deactivate members.");
    }

    log.info("Deactivating user: id={}, requesterId={}", id, requesterId);

    userService.deactivateUser(id, "Deactivated by " + requesterId);

    return ResponseEntity.ok(ApiResponse.success(null, "User deactivated successfully"));
  }

  /**
   * Check if contact value exists in current tenant (tenant-scoped).
   *
   * <p>Protection: auth required, tenant-scoped, rate limited to prevent enumeration.
   */
  @RateLimited(requests = 5, windowSeconds = 60)
  @PreAuthorize("isAuthenticated()")
  @GetMapping("/contact/{contactValue}")
  public ResponseEntity<ApiResponse<Boolean>> checkContactExists(
      @PathVariable String contactValue) {
    log.debug("Checking contact existence: {}", PiiMaskingUtil.maskEmail(contactValue));

    UUID tenantId = TenantContext.getCurrentTenantId();
    boolean exists = userService.contactExistsInTenant(tenantId, contactValue);

    return ResponseEntity.ok(ApiResponse.success(exists));
  }

  /**
   * Get current user profile (self-service endpoint).
   *
   * <p>Uses TenantContext.getCurrentUserId() to get the authenticated user.
   */
  @GetMapping("/me")
  public ResponseEntity<ApiResponse<UserDto>> getCurrentUser() {
    UUID userId = TenantContext.getCurrentUserId();
    UUID tenantId = TenantContext.getCurrentTenantId();

    if (userId == null) {
      throw new PlatformDomainException("User not authenticated", "USER_NOT_AUTHENTICATED", 401);
    }

    log.debug("Getting current user profile: userId={}", userId);

    UserDto user =
        userService
            .findByIdWithPermissionData(tenantId, userId)
            .orElseThrow(() -> new NotFoundException("User not found: " + userId));

    PermissionResult permResult =
        permissionEvaluator.evaluate(
            tenantId, user.getRoleCode(), user.getDepartmentCodes(), user.getId());

    user.setPermissions(permResult.permissions());
    user.setSuperAdmin(permResult.isSuperAdmin());

    return ResponseEntity.ok(ApiResponse.success(user));
  }

  /**
   * Update the authenticated user's own locale and timezone preferences (self-service).
   *
   * <p>Cascade order: User preference → Tenant settings → System default (EN / UTC)
   */
  @PatchMapping("/me/locale")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<ApiResponse<UserDto>> updateMyLocalePreferences(
      @Valid @RequestBody UpdateLocalePreferencesRequest request) {
    UUID userId = TenantContext.getCurrentUserId();
    if (userId == null) {
      throw new PlatformDomainException("User not authenticated", "USER_NOT_AUTHENTICATED", 401);
    }

    log.info(
        "Updating locale preferences: userId={}, locale={}, timezone={}",
        userId,
        request.preferredLocale(),
        request.preferredTimezone());

    UserDto updated = userLocaleService.updateLocalePreferences(userId, request);
    return ResponseEntity.ok(
        ApiResponse.success(updated, "Locale preferences updated successfully"));
  }

  /**
   * Get the current user's access level for team member management.
   *
   * <p>Returns: FULL_ACCESS, READ_ALL, DEPARTMENT_ONLY, or NO_ACCESS.
   */
  @GetMapping("/me/team-access")
  public ResponseEntity<ApiResponse<String>> getMyTeamAccess() {
    UUID requesterId = TenantContext.getCurrentUserId();
    if (requesterId == null) {
      throw new PlatformDomainException("User not authenticated", "USER_NOT_AUTHENTICATED", 401);
    }

    TeamAccessService.AccessLevel level = teamAccessService.resolveAccessLevel(requesterId);

    return ResponseEntity.ok(ApiResponse.success(level.name()));
  }

  /**
   * Get contact suggestions for user creation.
   *
   * <p>Returns intelligent suggestions based on company contacts:
   *
   * <ul>
   *   <li>Phone suggestion from company default phone
   *   <li>Email suggestions from company domain (multiple formats)
   * </ul>
   *
   * <p><b>Purpose:</b> Minimize manual data entry during user creation.
   *
   * @param organizationId Organization ID
   * @param firstName User first name
   * @param lastName User last name
   * @return Contact suggestions
   */
  @GetMapping("/contact-suggestions")
  public ResponseEntity<ApiResponse<ContactSuggestionsDto>> getContactSuggestions(
      @RequestParam UUID organizationId,
      @RequestParam String firstName,
      @RequestParam String lastName) {
    log.debug(
        "Getting contact suggestions: organizationId={}, firstName={}, lastName={}",
        organizationId,
        firstName,
        lastName);

    ContactSuggestionsDto suggestions =
        contactSuggestionService.getSuggestions(organizationId, firstName, lastName);

    return ResponseEntity.ok(ApiResponse.success(suggestions));
  }

  /**
   * Generate employee number (global auto-incrementing sequence).
   *
   * <p><b>Format:</b> {TENANT_UID}-EMP-{SEQUENCE}
   *
   * <p><b>Example:</b> "ACME-001-EMP-00042"
   *
   * <p>Format breakdown:
   *
   * <ul>
   *   <li>{TENANT_UID}: Company tenant UID (e.g., "ACME-001")
   *   <li>EMP: Employee module code
   *   <li>{SEQUENCE}: Auto-incrementing global sequence (e.g., "00042")
   * </ul>
   *
   * <p><b>Design:</b> Global sequence (not year-based) to ensure:
   *
   * <ul>
   *   <li>✅ No sequence reset at year boundary
   *   <li>✅ Unique numbers (never duplicates)
   *   <li>✅ Supports unlimited employees
   * </ul>
   *
   * <p>This endpoint is used by frontend "magic wand" button to auto-generate employee numbers when
   * creating new users.
   *
   * @return Generated employee number
   */
  @GetMapping("/generate-employee-number")
  public ResponseEntity<ApiResponse<String>> generateEmployeeNumber() {
    log.debug("Generating employee number");

    String employeeNumber = employeeCreationPort.generateEmployeeNumber();

    return ResponseEntity.ok(ApiResponse.success(employeeNumber));
  }

  /** Get options for creating an internal employee. Returns INTERNAL-scoped roles + departments. */
  @GetMapping("/creation-options")
  public ResponseEntity<
          ApiResponse<com.fabricmanagement.platform.subscription.dto.UserCreationOptionsDto>>
      getUserCreationOptions() {
    log.debug("Getting internal user creation options");

    com.fabricmanagement.platform.subscription.dto.UserCreationOptionsDto options =
        userCreationOptionsService.getUserCreationOptions();

    return ResponseEntity.ok(ApiResponse.success(options));
  }

  /** Get options for inviting a partner user. Returns PARTNER-scoped roles + departments. */
  @GetMapping("/creation-options/partner")
  public ResponseEntity<
          ApiResponse<com.fabricmanagement.platform.subscription.dto.UserCreationOptionsDto>>
      getPartnerCreationOptions() {
    log.debug("Getting partner user creation options");

    com.fabricmanagement.platform.subscription.dto.UserCreationOptionsDto options =
        userCreationOptionsService.getPartnerCreationOptions();

    return ResponseEntity.ok(ApiResponse.success(options));
  }
}
