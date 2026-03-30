package com.fabricmanagement.platform.user.app;

import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.platform.communication.app.AddressService;
import com.fabricmanagement.platform.communication.app.ContactService;
import com.fabricmanagement.platform.communication.domain.AddressType;
import com.fabricmanagement.platform.communication.domain.ContactType;
import com.fabricmanagement.platform.communication.dto.CreateAddressRequest;
import com.fabricmanagement.platform.organization.app.OrganizationAddressAssignmentService;
import com.fabricmanagement.platform.organization.app.OrganizationContactAssignmentService;
import com.fabricmanagement.platform.organization.app.OrganizationService;
import com.fabricmanagement.platform.organization.dto.OrganizationDto;
import com.fabricmanagement.platform.user.domain.User;
import com.fabricmanagement.platform.user.domain.event.UserOnboardingCompletedEvent;
import com.fabricmanagement.platform.user.domain.port.EmployeeProjectionPort;
import com.fabricmanagement.platform.user.dto.CompleteOnboardingRequest;
import com.fabricmanagement.platform.user.dto.OnboardingStatusResponse;
import com.fabricmanagement.platform.user.dto.UserDto;
import com.fabricmanagement.platform.user.infra.repository.UserRepository;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
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
  private final EmployeeProjectionPort employeeProjectionPort;
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
      return UserDto.from(user, employeeProjectionPort.findByUserId(userId).orElse(null));
    }

    if (request != null) {
      applyEnrichment(tenantId, request);
    }

    user.completeOnboarding();
    User saved = userRepository.save(user);

    eventPublisher.publish(new UserOnboardingCompletedEvent(saved.getTenantId(), saved.getId()));

    log.info("Onboarding completed: userId={}, uid={}", saved.getId(), saved.getUid());

    return UserDto.from(saved, employeeProjectionPort.findByUserId(saved.getId()).orElse(null));
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
      String label =
          (req.getAddressLabel() != null && !req.getAddressLabel().isBlank())
              ? req.getAddressLabel().trim()
              : "Headquarters";

      CreateAddressRequest createRequest =
          CreateAddressRequest.builder()
              .streetAddress(req.getAddressLine1() != null ? req.getAddressLine1().trim() : "")
              .addressLine2(req.getAddressLine2() != null ? req.getAddressLine2().trim() : null)
              .city(req.getCity() != null ? req.getCity().trim() : "")
              .state(req.getState() != null ? req.getState().trim() : null)
              .district(req.getDistrict() != null ? req.getDistrict().trim() : null)
              .postalCode(req.getPostalCode() != null ? req.getPostalCode().trim() : null)
              .country(req.getCountry() != null ? req.getCountry().trim() : "")
              .countryCode(null)
              .addressType(AddressType.HEADQUARTERS)
              .label(label)
              .build();

      var address = addressService.createAddress(createRequest);
      addressAssignmentService.assignAddress(orgId, address.getId(), true, true);
      log.debug("saveHqAddress: addressId={}, orgId={}", address.getId(), orgId);
    } catch (DataAccessException | IllegalArgumentException ex) {
      log.warn("saveHqAddress: failed for orgId={} — {}", orgId, ex.getMessage());
    } catch (Exception ex) {
      log.warn("saveHqAddress: unexpected error for orgId={} — {}", orgId, ex.getMessage(), ex);
      throw ex;
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
