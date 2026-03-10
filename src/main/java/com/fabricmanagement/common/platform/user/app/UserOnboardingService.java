package com.fabricmanagement.common.platform.user.app;

import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.platform.communication.app.AddressService;
import com.fabricmanagement.common.platform.communication.app.ContactService;
import com.fabricmanagement.common.platform.communication.domain.AddressType;
import com.fabricmanagement.common.platform.communication.domain.ContactType;
import com.fabricmanagement.common.platform.organization.app.OrganizationAddressAssignmentService;
import com.fabricmanagement.common.platform.organization.app.OrganizationContactAssignmentService;
import com.fabricmanagement.common.platform.organization.app.OrganizationService;
import com.fabricmanagement.common.platform.organization.dto.OrganizationDto;
import com.fabricmanagement.common.platform.user.domain.User;
import com.fabricmanagement.common.platform.user.domain.event.UserOnboardingCompletedEvent;
import com.fabricmanagement.common.platform.user.dto.CompleteOnboardingRequest;
import com.fabricmanagement.common.platform.user.dto.OnboardingStatusResponse;
import com.fabricmanagement.common.platform.user.dto.UserDto;
import com.fabricmanagement.common.platform.user.infra.repository.UserRepository;
import com.fabricmanagement.human.core.employee.application.EmployeeService;
import com.fabricmanagement.human.core.employee.domain.Employee;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * User onboarding service — status and completion.
 *
 * <p>Supports two call modes:
 *
 * <ol>
 *   <li>Legacy — {@code completeOnboarding(userId, null)}: marks user as onboarded, no enrichment.
 *   <li>Enriched — {@code completeOnboarding(userId, request)}: marks user as onboarded AND
 *       persists company profile data (legalName, industry, address, etc.) to the root
 *       Organization.
 * </ol>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserOnboardingService {

  private final UserRepository userRepository;
  private final EmployeeService employeeService;
  private final DomainEventPublisher eventPublisher;
  private final OrganizationService organizationService;
  private final AddressService addressService;
  private final ContactService contactService;
  private final OrganizationAddressAssignmentService addressAssignmentService;
  private final OrganizationContactAssignmentService contactAssignmentService;

  @Transactional(readOnly = true)
  public OnboardingStatusResponse getStatus(UUID userId) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    log.trace("Getting onboarding status: tenantId={}, userId={}", tenantId, userId);

    User user =
        userRepository
            .findByTenantIdAndId(tenantId, userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

    boolean completed = user.getOnboardingCompletedAt() != null;
    Instant completedAt = user.getOnboardingCompletedAt();

    return OnboardingStatusResponse.builder()
        .hasCompletedOnboarding(completed)
        .completedAt(completedAt)
        .build();
  }

  /**
   * Complete onboarding and optionally enrich the tenant's root organization.
   *
   * @param userId Authenticated user UUID
   * @param request Enrichment data (may be null — skips enrichment, marks completion only)
   * @return Updated UserDto
   */
  @Transactional
  public UserDto completeOnboarding(UUID userId, CompleteOnboardingRequest request) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    log.info("Completing onboarding: tenantId={}, userId={}", tenantId, userId);

    User user =
        userRepository
            .findByTenantIdAndId(tenantId, userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

    if (user.getOnboardingCompletedAt() != null) {
      log.debug("User already completed onboarding: userId={}", userId);
      if (request != null) {
        applyEnrichment(tenantId, request);
      }
      Employee employee = employeeService.getEmployeeByUserId(userId).orElse(null);
      return UserDto.from(user, employee);
    }

    if (request != null) {
      applyEnrichment(tenantId, request);
    }

    user.completeOnboarding();
    User saved = userRepository.save(user);

    eventPublisher.publish(new UserOnboardingCompletedEvent(saved.getTenantId(), saved.getId()));

    log.info("Onboarding completed: userId={}, uid={}", saved.getId(), saved.getUid());

    Employee employee = employeeService.getEmployeeByUserId(saved.getId()).orElse(null);
    return UserDto.from(saved, employee);
  }

  @Transactional(readOnly = true)
  public boolean hasCompletedOnboarding(UUID userId) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    User user =
        userRepository
            .findByTenantIdAndId(tenantId, userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));
    return user.hasCompletedOnboarding();
  }

  // ── Private helpers ──────────────────────────────────────────────────────────

  /**
   * Persist enrichment data to the tenant's root organization:
   *
   * <ol>
   *   <li>Company profile fields (legalName, industry, website, etc.)
   *   <li>Headquarters address (created fresh; existing HQ address not duplicated if one exists)
   *   <li>Company contacts (email and/or phone)
   * </ol>
   */
  private void applyEnrichment(UUID tenantId, CompleteOnboardingRequest request) {
    log.debug("Applying onboarding enrichment: tenantId={}", tenantId);

    organizationService.enrichRootOrganization(tenantId, request);

    OrganizationDto rootOrg = organizationService.getRootOrganization().orElse(null);
    if (rootOrg == null) {
      log.warn("applyEnrichment: root organization not found, skipping address/contact save");
      return;
    }
    UUID orgId = rootOrg.getId();

    saveHqAddress(orgId, request);
    saveContacts(orgId, request);
  }

  private void saveHqAddress(UUID orgId, CompleteOnboardingRequest req) {
    if (req.getAddressLine1() == null && req.getCity() == null && req.getCountry() == null) {
      return;
    }
    try {
      var address =
          addressService.createAddress(
              req.getAddressLine1() != null ? req.getAddressLine1().trim() : "",
              req.getCity() != null ? req.getCity().trim() : "",
              req.getState() != null ? req.getState().trim() : null,
              req.getDistrict() != null ? req.getDistrict().trim() : null,
              req.getPostalCode() != null ? req.getPostalCode().trim() : null,
              req.getCountry() != null ? req.getCountry().trim() : "",
              null,
              AddressType.HEADQUARTERS,
              "Headquarters",
              req.getAddressLine2() != null ? req.getAddressLine2().trim() : null);

      addressAssignmentService.assignAddress(orgId, address.getId(), true, true);
      log.debug("saveHqAddress: addressId={}, orgId={}", address.getId(), orgId);
    } catch (Exception ex) {
      log.warn("saveHqAddress: failed for orgId={} — {}", orgId, ex.getMessage());
    }
  }

  private void saveContacts(UUID orgId, CompleteOnboardingRequest req) {
    if (req.getCompanyEmail() != null && !req.getCompanyEmail().isBlank()) {
      try {
        var contact =
            contactService.createContact(
                req.getCompanyEmail().trim(), ContactType.EMAIL, "Organization", false, null);
        contactAssignmentService.assignContact(orgId, contact.getId(), true, null);
        log.debug("saveContacts: email assigned, orgId={}", orgId);
      } catch (Exception ex) {
        log.warn("saveContacts: email failed for orgId={} — {}", orgId, ex.getMessage());
      }
    }

    if (req.getCompanyPhone() != null && !req.getCompanyPhone().isBlank()) {
      try {
        var contact =
            contactService.createContact(
                req.getCompanyPhone().trim(), ContactType.MOBILE, "Organization", false, null);
        contactAssignmentService.assignContact(orgId, contact.getId(), false, null);
        log.debug("saveContacts: phone assigned, orgId={}", orgId);
      } catch (Exception ex) {
        log.warn("saveContacts: phone failed for orgId={} — {}", orgId, ex.getMessage());
      }
    }
  }
}
