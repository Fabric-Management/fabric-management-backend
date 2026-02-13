package com.fabricmanagement.common.platform.user.api.controller;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.common.infrastructure.web.rate.RateLimited;
import com.fabricmanagement.common.platform.communication.app.ContactSuggestionService;
import com.fabricmanagement.common.platform.communication.dto.ContactSuggestionsDto;
import com.fabricmanagement.common.platform.user.app.UserAddressAssignmentService;
import com.fabricmanagement.common.platform.user.app.UserContactAssignmentService;
import com.fabricmanagement.common.platform.user.app.UserService;
import com.fabricmanagement.common.platform.user.dto.CreateExternalUserRequest;
import com.fabricmanagement.common.platform.user.dto.CreateInternalUserRequest;
import com.fabricmanagement.common.platform.user.dto.UpdateUserRequest;
import com.fabricmanagement.common.platform.user.dto.UserAddressDto;
import com.fabricmanagement.common.platform.user.dto.UserContactDto;
import com.fabricmanagement.common.platform.user.dto.UserDto;
import com.fabricmanagement.common.util.PiiMaskingUtil;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/common/users")
@RequiredArgsConstructor
@Slf4j
public class UserController {

  private final UserService userService;
  private final ContactSuggestionService contactSuggestionService;
  private final UserContactAssignmentService userContactAssignmentService;
  private final UserAddressAssignmentService userAddressAssignmentService;
  private final com.fabricmanagement.human.core.employee.application.EmployeeService
      employeeService;
  private final com.fabricmanagement.common.platform.subscription.app.UserCreationOptionsService
      userCreationOptionsService;

  /**
   * Create internal employee (own staff with HR data).
   *
   * <p><b>Use Case:</b> Creating employees for your own company (tenant users with HR records).
   *
   * <p><b>Includes:</b> Title, gender, birth date, nationality, emergency contact, employee number,
   * hire date.
   */
  @PostMapping("/internal")
  public ResponseEntity<ApiResponse<UserDto>> createInternalUser(
      @Valid @RequestBody CreateInternalUserRequest request) {
    log.info(
        "Creating internal employee: contactValue={}",
        PiiMaskingUtil.maskEmail(request.getContactValue()));

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
  @PostMapping("/external")
  public ResponseEntity<ApiResponse<UserDto>> createExternalUser(
      @Valid @RequestBody CreateExternalUserRequest request) {
    log.info(
        "Creating external user: contactValue={}",
        PiiMaskingUtil.maskEmail(request.getContactValue()));

    UserDto created = userService.createExternalUser(request);

    return ResponseEntity.ok(ApiResponse.success(created, "External user created successfully"));
  }

  @GetMapping("/{id}")
  public ResponseEntity<ApiResponse<UserDto>> getUser(@PathVariable UUID id) {
    log.debug("Getting user: id={}", id);

    UserDto user =
        userService
            .findById(
                com.fabricmanagement.common.infrastructure.persistence.TenantContext
                    .getCurrentTenantId(),
                id)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

    return ResponseEntity.ok(ApiResponse.success(user));
  }

  /**
   * Get all users for current tenant.
   *
   * <p><b>Cached:</b> 5 minutes (tenant-scoped cache key)
   */
  @GetMapping
  @Cacheable(
      value = "users",
      key =
          "T(com.fabricmanagement.common.infrastructure.persistence.TenantContext).getCurrentTenantId()")
  public ResponseEntity<ApiResponse<List<UserDto>>> getAllUsers() {
    log.debug("Getting all users");

    UUID tenantId =
        com.fabricmanagement.common.infrastructure.persistence.TenantContext.getCurrentTenantId();
    List<UserDto> users = userService.findByTenant(tenantId);

    return ResponseEntity.ok(ApiResponse.success(users));
  }

  @GetMapping("/company/{companyId}")
  public ResponseEntity<ApiResponse<List<UserDto>>> getUsersByCompany(
      @PathVariable UUID companyId) {
    log.debug("Getting users by company: companyId={}", companyId);

    List<UserDto> users =
        userService.findByCompany(
            com.fabricmanagement.common.infrastructure.persistence.TenantContext
                .getCurrentTenantId(),
            companyId);

    return ResponseEntity.ok(ApiResponse.success(users));
  }

  @PutMapping("/{id}")
  public ResponseEntity<ApiResponse<UserDto>> updateUser(
      @PathVariable UUID id, @Valid @RequestBody UpdateUserRequest request) {
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
    UUID userId =
        com.fabricmanagement.common.infrastructure.persistence.TenantContext.getCurrentUserId();

    if (userId == null) {
      throw new IllegalStateException("User not authenticated");
    }

    log.debug("Getting current user profile: userId={}", userId);

    UserDto user =
        userService
            .findById(
                com.fabricmanagement.common.infrastructure.persistence.TenantContext
                    .getCurrentTenantId(),
                userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

    return ResponseEntity.ok(ApiResponse.success(user));
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
   * @param companyId Company ID
   * @param firstName User first name
   * @param lastName User last name
   * @return Contact suggestions
   */
  @GetMapping("/contact-suggestions")
  public ResponseEntity<ApiResponse<ContactSuggestionsDto>> getContactSuggestions(
      @RequestParam UUID companyId, @RequestParam String firstName, @RequestParam String lastName) {
    log.debug(
        "Getting contact suggestions: companyId={}, firstName={}, lastName={}",
        companyId,
        firstName,
        lastName);

    ContactSuggestionsDto suggestions =
        contactSuggestionService.getSuggestions(companyId, firstName, lastName);

    return ResponseEntity.ok(ApiResponse.success(suggestions));
  }

  /**
   * Get user contacts.
   *
   * <p>Returns all contacts assigned to the user, including:
   *
   * <ul>
   *   <li>Contact details (value, type, verification status)
   *   <li>Default contact flag
   *   <li>Authentication contact flag
   * </ul>
   *
   * @param id User ID
   * @return List of user contacts
   */
  @GetMapping("/{id}/contacts")
  public ResponseEntity<ApiResponse<List<UserContactDto>>> getUserContacts(@PathVariable UUID id) {
    log.debug("Getting user contacts: userId={}", id);

    // Validate user exists (tenant-scoped)
    userService
        .findById(
            com.fabricmanagement.common.infrastructure.persistence.TenantContext
                .getCurrentTenantId(),
            id)
        .orElseThrow(() -> new IllegalArgumentException("User not found"));

    List<UserContactDto> contacts =
        userContactAssignmentService.getUserContacts(id).stream()
            .map(UserContactDto::from)
            .toList();

    return ResponseEntity.ok(ApiResponse.success(contacts));
  }

  /**
   * Get user addresses.
   *
   * <p>Returns all addresses assigned to the user, including:
   *
   * <ul>
   *   <li>Address details (full address, coordinates, type)
   *   <li>Primary address flag
   *   <li>Work address flag
   * </ul>
   *
   * @param id User ID
   * @return List of user addresses
   */
  @GetMapping("/{id}/addresses")
  public ResponseEntity<ApiResponse<List<UserAddressDto>>> getUserAddresses(@PathVariable UUID id) {
    log.debug("Getting user addresses: userId={}", id);

    // Validate user exists (tenant-scoped)
    userService
        .findById(
            com.fabricmanagement.common.infrastructure.persistence.TenantContext
                .getCurrentTenantId(),
            id)
        .orElseThrow(() -> new IllegalArgumentException("User not found"));

    List<UserAddressDto> addresses =
        userAddressAssignmentService.getUserAddresses(id).stream()
            .map(UserAddressDto::from)
            .toList();

    return ResponseEntity.ok(ApiResponse.success(addresses));
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

    String employeeNumber = employeeService.generateEmployeeNumber();

    return ResponseEntity.ok(ApiResponse.success(employeeNumber));
  }

  /**
   * Get all options needed for user creation form.
   *
   * <p><b>Orchestration Endpoint:</b> Returns department categories, departments, and positions in
   * a single response to optimize frontend form loading.
   *
   * <p><b>Performance Benefits:</b>
   *
   * <ul>
   *   <li>✅ Single HTTP request instead of 3 separate requests
   *   <li>✅ Reduced network overhead
   *   <li>✅ Faster page load (parallel loading not needed)
   *   <li>✅ Single database transaction
   * </ul>
   *
   * <p>This endpoint should be called when the user creation modal/form is opened to populate all
   * dropdown options.
   *
   * @return User creation options (categories, departments, positions)
   */
  @GetMapping("/creation-options")
  public ResponseEntity<
          ApiResponse<com.fabricmanagement.common.platform.subscription.dto.UserCreationOptionsDto>>
      getUserCreationOptions() {
    log.debug("Getting user creation options");

    com.fabricmanagement.common.platform.subscription.dto.UserCreationOptionsDto options =
        userCreationOptionsService.getUserCreationOptions();

    return ResponseEntity.ok(ApiResponse.success(options));
  }
}
