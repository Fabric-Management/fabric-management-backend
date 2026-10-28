package com.fabricmanagement.platform.user.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.platform.common.exception.PlatformDomainException;
import com.fabricmanagement.platform.communication.domain.ContactType;
import com.fabricmanagement.platform.lead.domain.CompanyEvidence;
import com.fabricmanagement.platform.organization.app.CompanyOnboardingProfileService;
import com.fabricmanagement.platform.subscription.app.EvaluationSubscriptionService;
import com.fabricmanagement.platform.user.domain.*;
import com.fabricmanagement.platform.user.domain.port.OnboardingEvidencePort;
import com.fabricmanagement.platform.user.dto.*;
import com.fabricmanagement.platform.user.infra.repository.*;
import com.fabricmanagement.platform.user.security.OnboardingAccessService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.validation.Validator;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Accepted step, live profile, retained evidence and frozen receipt share one transaction. */
@Service
@RequiredArgsConstructor
public class OnboardingJourneyService {
  private final OnboardingJourneyRepository journeys;
  private final OnboardingOperationRepository operations;
  private final OnboardingAccessService access;
  private final OnboardingCommandLockService userLock;
  private final OnboardingEvidencePort retained;
  private final CompanyOnboardingProfileService companyProfiles;
  private final UserProfileService personalProfiles;
  private final UserOnboardingService onboarding;
  private final OnboardingEvidenceMapper evidenceMapper;
  private final OnboardingJourneyViewMapper views;
  private final OnboardingRequestValidator profileValidator;
  private final EvaluationSubscriptionService evaluations;
  private final ObjectMapper json;
  private final Validator validator;
  private final EntityManager entities;

  @Transactional(readOnly = true)
  public OnboardingJourneyResponse get() {
    User user = access.requireCurrentUser(TenantContext.getCurrentUserId());
    var capability = access.companySetupCapability(user);
    OnboardingJourney journey = resolve(user, capability.allowed());
    var evidence =
        journey.getKind() == OnboardingJourney.Kind.COMPANY && capability.allowed()
            ? retained.current(user.getTenantId(), journey.getSignupId())
            : null;
    return view(journey, user, capability, evidence, List.of());
  }

  @Transactional
  public OnboardingJourneyResponse save(OnboardingStepId step, SaveOnboardingStepRequest command) {
    User user = lockedActor();
    var capability = access.companySetupCapability(user);
    OnboardingJourney journey = lockedJourney(user, capability.allowed());
    requireAuthorised(journey, user, step);
    validate(command);
    if (step != command.payload().id()) throw invalid("ONBOARDING_STEP_INVALID");
    String hash = hash("STEP:" + step, command);
    var previous = replay(journey, user, command.operationId(), hash);
    if (previous != null) return previous;
    requireVersion(journey, command.expectedVersion());
    journey.requireOrder(step);
    if (command.payload() instanceof OnboardingStepPayload.Review review
        && !Objects.equals(review.reviewedVersion(), command.expectedVersion())) {
      throw conflict(journey, "ONBOARDING_REVIEW_STALE");
    }
    if (command.payload() instanceof OnboardingStepPayload.PlatformModules modules) {
      evaluations.select(
          user.getTenantId(),
          journey.getSignupIntent(),
          journey.getCompletedAt() == null,
          modules.selectedOS(),
          modules.catalogueVersion());
    }
    var patch = evidenceMapper.patch(command.payload());
    profileValidator.validate(patch);
    CompanyEvidence before =
        journey.getKind() == OnboardingJourney.Kind.COMPANY
            ? retained.current(user.getTenantId(), journey.getSignupId())
            : null;
    CompanyEvidence accepted = before == null ? null : evidenceMapper.merge(before, patch);
    var profile =
        patch.hasCompanyChanges() && step != OnboardingStepId.PLATFORM_MODULES
            ? companyProfiles.prepare(patch)
            : null;
    // Every validation/preflight above precedes personal and company profile writes.
    if (patch.getMobilePhone() != null) {
      personalProfiles.updatePersonalContact(
          user.getId(), patch.getMobilePhone(), ContactType.MOBILE);
      journey.acceptPersonalMobile(patch.getMobilePhone());
    }
    if (profile != null) companyProfiles.save(profile);
    boolean changed = before == null || !before.equals(accepted);
    List<OnboardingStepId> invalidated = journey.accept(step, command.operationId(), changed);
    journeys.saveAndFlush(journey);
    if (accepted != null)
      retained.accept(
          user.getTenantId(),
          journey.getSignupId(),
          user.getId(),
          command.operationId(),
          step,
          accepted,
          null);
    var response = view(journey, user, capability, accepted, invalidated);
    remember(journey, user, command.operationId(), hash, response);
    return response;
  }

  @Transactional
  public OnboardingJourneyResponse complete(CompleteOnboardingJourneyRequest command) {
    User user = lockedActor();
    var capability = access.companySetupCapability(user);
    OnboardingJourney journey = lockedJourney(user, capability.allowed());
    requireAuthorised(journey, user, OnboardingStepId.REVIEW);
    validate(command);
    String hash = hash("COMPLETE", command);
    var previous = replay(journey, user, command.operationId(), hash);
    if (previous != null) return previous;
    requireVersion(journey, command.expectedVersion());
    var evidence =
        journey.getKind() == OnboardingJourney.Kind.COMPANY
            ? retained.current(user.getTenantId(), journey.getSignupId())
            : null;
    boolean finalize = journey.needsCompletion();
    journey.finish(command.reviewOperationId());
    onboarding.completeAcceptedJourney(user.getId());
    journeys.saveAndFlush(journey);
    if (evidence != null && finalize)
      retained.accept(
          user.getTenantId(),
          journey.getSignupId(),
          user.getId(),
          command.operationId(),
          OnboardingStepId.REVIEW,
          evidence,
          journey.getCompletedAt());
    var response = view(journey, user, capability, evidence, List.of());
    remember(journey, user, command.operationId(), hash, response);
    return response;
  }

  private User lockedActor() {
    User user = access.requireCurrentUser(TenantContext.getCurrentUserId());
    userLock.lockAndRefresh(user);
    return access.requireCurrentUser(user.getId());
  }

  private OnboardingJourneyResponse view(
      OnboardingJourney journey,
      User user,
      OnboardingCompanyCapability capability,
      CompanyEvidence evidence,
      List<OnboardingStepId> invalidated) {
    var selection =
        journey.getKind() == OnboardingJourney.Kind.COMPANY && capability.allowed()
            ? evaluations.state(
                user.getTenantId(), journey.getSignupIntent(), journey.getCompletedAt() == null)
            : null;
    return views.view(journey, user, capability, evidence, invalidated, selection);
  }

  private OnboardingJourney resolve(User user, boolean canConfigure) {
    var company =
        journeys.findByTenantIdAndKind(user.getTenantId(), OnboardingJourney.Kind.COMPANY);
    if (company.isPresent()
        && (company.get().getOwnerUserId().equals(user.getId()) || canConfigure))
      return company.get();
    var recorded = journeys.findByTenantIdAndOwnerUserId(user.getTenantId(), user.getId());
    if (company.isEmpty()
        && canConfigure
        && recorded
            .filter(journey -> journey.getKind() == OnboardingJourney.Kind.PERSONAL)
            .isEmpty()) {
      throw new PlatformDomainException(
          "Resolve the company's signup linkage before continuing onboarding",
          "ONBOARDING_SIGNUP_LINK_UNRESOLVED",
          409);
    }
    var personal =
        recorded.orElseGet(() -> OnboardingJourney.personal(user.getTenantId(), user.getId()));
    if (personal.getId() == null)
      personal.preservePersonalLegacyCompletion(user.getOnboardingCompletedAt());
    return personal;
  }

  private OnboardingJourney lockedJourney(User user, boolean canConfigure) {
    var journey = resolve(user, canConfigure);
    if (journey.getId() != null) {
      // resolve() populated the persistence context; a lock query alone can keep a stale version.
      entities.refresh(journey, LockModeType.PESSIMISTIC_WRITE);
    }
    return journey;
  }

  private void requireAuthorised(OnboardingJourney journey, User user, OnboardingStepId step) {
    if (journey.getKind() == OnboardingJourney.Kind.COMPANY) access.requireCompanySetup(user);
    boolean personal =
        step == OnboardingStepId.YOUR_DETAILS
            || step == OnboardingStepId.REVIEW
            || step == OnboardingStepId.PLATFORM_MODULES;
    if (personal && !journey.getOwnerUserId().equals(user.getId())) {
      throw new PlatformDomainException(
          "Only the registrant can accept personal details or final review",
          "ONBOARDING_REGISTRANT_REQUIRED",
          403);
    }
  }

  private OnboardingJourneyResponse replay(
      OnboardingJourney journey, User user, UUID operation, String hash) {
    if (journey.getId() == null) return null;
    var receipt =
        operations.findByTenantIdAndJourneyIdAndOperationId(
            user.getTenantId(), journey.getId(), operation);
    if (receipt.isEmpty()) return null;
    var saved = receipt.get();
    if (!saved.getActorUserId().equals(user.getId()) || !saved.getCommandHash().equals(hash)) {
      throw conflict(journey, "ONBOARDING_OPERATION_CONFLICT");
    }
    return json.convertValue(saved.getResult(), OnboardingJourneyResponse.class);
  }

  private void remember(
      OnboardingJourney journey,
      User user,
      UUID operation,
      String hash,
      OnboardingJourneyResponse response) {
    operations.save(
        OnboardingOperation.accepted(
            journey, operation, user.getId(), hash, json.valueToTree(response)));
  }

  private void requireVersion(OnboardingJourney journey, long expected) {
    if (journey.getCommandVersion() != expected)
      throw conflict(journey, "ONBOARDING_VERSION_CONFLICT");
  }

  private PlatformDomainException conflict(OnboardingJourney journey, String code) {
    return new PlatformDomainException("Onboarding evidence changed; reload the journey", code, 409)
        .withDetail("currentVersion", journey.getCommandVersion())
        .withDetail("firstIncompleteStep", journey.firstIncomplete());
  }

  private void validate(Object command) {
    if (command == null || !validator.validate(command).isEmpty())
      throw invalid("ONBOARDING_STEP_INVALID");
  }

  private PlatformDomainException invalid(String code) {
    return new PlatformDomainException("Invalid onboarding step command", code, 422);
  }

  private String hash(String operation, Object command) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(
                      (operation + ":" + json.writeValueAsString(command))
                          .getBytes(StandardCharsets.UTF_8)));
    } catch (JsonProcessingException | NoSuchAlgorithmException failure) {
      throw new PlatformDomainException(
          "Unable to freeze onboarding command", "ONBOARDING_COMMAND_ENCODING_FAILED", 500);
    }
  }
}
