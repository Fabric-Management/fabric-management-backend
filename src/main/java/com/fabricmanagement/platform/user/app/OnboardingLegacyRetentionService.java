package com.fabricmanagement.platform.user.app;

import com.fabricmanagement.platform.common.exception.PlatformDomainException;
import com.fabricmanagement.platform.user.domain.OnboardingJourney;
import com.fabricmanagement.platform.user.domain.User;
import com.fabricmanagement.platform.user.domain.port.OnboardingEvidencePort;
import com.fabricmanagement.platform.user.dto.CompleteOnboardingRequest;
import com.fabricmanagement.platform.user.infra.repository.OnboardingJourneyRepository;
import com.fabricmanagement.platform.user.security.OnboardingAccessService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Compatibility guard: personal callers remain supported; registered owners cannot skip steps. */
@Service
@RequiredArgsConstructor
public class OnboardingLegacyRetentionService {
  private final OnboardingJourneyRepository journeys;
  private final OnboardingEvidencePort retained;
  private final OnboardingEvidenceMapper mapper;
  private final EntityManager entities;
  private final OnboardingAccessService access;

  @Transactional(propagation = Propagation.MANDATORY)
  public OnboardingJourney prepare(User user, CompleteOnboardingRequest request) {
    var company =
        journeys.findLockedByTenantIdAndKind(user.getTenantId(), OnboardingJourney.Kind.COMPANY);
    if (company.isEmpty()) {
      boolean companyChanges = request != null && request.hasCompanyChanges();
      boolean recordedPersonal =
          journeys
              .findByTenantIdAndOwnerUserId(user.getTenantId(), user.getId())
              .filter(journey -> journey.getKind() == OnboardingJourney.Kind.PERSONAL)
              .isPresent();
      // Missing linkage is not evidence that a company administrator is a personal registrant.
      if (companyChanges || (!recordedPersonal && access.companySetupCapability(user).allowed())) {
        throw new PlatformDomainException(
            "Resolve the company's signup linkage before continuing onboarding",
            "ONBOARDING_SIGNUP_LINK_UNRESOLVED",
            409);
      }
      return null;
    }
    var journey = company.get();
    entities.refresh(journey, LockModeType.PESSIMISTIC_WRITE);
    boolean owner = journey.getOwnerUserId().equals(user.getId());
    boolean companyChanges = request != null && request.hasCompanyChanges();
    if (journey.getCompletedAt() == null && (owner || companyChanges)) {
      throw new PlatformDomainException(
              "Save and review the registered company's onboarding steps",
              "ONBOARDING_JOURNEY_REQUIRED",
              409)
          .withDetail("currentVersion", journey.getCommandVersion());
    }
    if (request != null && request.getSelectedOS() != null)
      throw new PlatformDomainException(
          "Use the versioned modules step to change evaluation access",
          "ONBOARDING_MODULES_STEP_REQUIRED",
          409);
    return request != null && (companyChanges || owner) ? journey : null;
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void retain(OnboardingJourney journey, User user, CompleteOnboardingRequest request) {
    if (journey == null || request == null) return;
    var safe = copy(request);
    // An administrator's own mobile must never replace the registered owner's retained mobile.
    if (!journey.getOwnerUserId().equals(user.getId())) safe.setMobilePhone(null);
    var previous = retained.current(user.getTenantId(), journey.getSignupId());
    var next = mapper.merge(previous, safe);
    if (previous.equals(next)) return;
    journey.retainedLegacyCorrection();
    retained.correctLegacy(
        user.getTenantId(),
        journey.getSignupId(),
        user.getId(),
        UUID.randomUUID(),
        next,
        journey.getCompletedAt());
  }

  private CompleteOnboardingRequest copy(CompleteOnboardingRequest request) {
    // Deliberately avoid mutating the caller's frozen HTTP command.
    return CompleteOnboardingRequest.builder()
        .organizationName(request.getOrganizationName())
        .organizationType(request.getOrganizationType())
        .taxId(request.getTaxId())
        .legalName(request.getLegalName())
        .registrationNumber(request.getRegistrationNumber())
        .industry(request.getIndustry())
        .website(request.getWebsite())
        .description(request.getDescription())
        .addressLine1(request.getAddressLine1())
        .addressLine2(request.getAddressLine2())
        .city(request.getCity())
        .state(request.getState())
        .district(request.getDistrict())
        .postalCode(request.getPostalCode())
        .country(request.getCountry())
        .addressLabel(request.getAddressLabel())
        .companyEmail(request.getCompanyEmail())
        .companyPhone(request.getCompanyPhone())
        .mobilePhone(request.getMobilePhone())
        .selectedOS(request.getSelectedOS())
        .build();
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void completePersonal(User user) {
    journeys
        .findByTenantIdAndOwnerUserId(user.getTenantId(), user.getId())
        .filter(journey -> journey.getKind() == OnboardingJourney.Kind.PERSONAL)
        .ifPresent(journey -> journey.completePersonalLegacy(user.getOnboardingCompletedAt()));
  }
}
