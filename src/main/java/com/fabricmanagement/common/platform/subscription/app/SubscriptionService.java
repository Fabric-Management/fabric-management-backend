package com.fabricmanagement.common.platform.subscription.app;

import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.platform.subscription.domain.Subscription;
import com.fabricmanagement.common.platform.subscription.domain.SubscriptionStatus;
import com.fabricmanagement.common.platform.subscription.domain.event.SubscriptionActivatedEvent;
import com.fabricmanagement.common.platform.subscription.domain.event.SubscriptionExpiredEvent;
import com.fabricmanagement.common.platform.subscription.dto.CreateInitialSubscriptionsResult;
import com.fabricmanagement.common.platform.subscription.dto.SubscriptionDto;
import com.fabricmanagement.common.platform.subscription.dto.UpdateSubscriptionRequest;
import com.fabricmanagement.common.platform.subscription.infra.repository.SubscriptionRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Subscription Service - CRITICAL for Policy Engine Layer 1.
 *
 * <p>Manages OS subscriptions which gate access to all system features. Before any operation,
 * Policy Engine checks: "Does tenant have active OS subscription?"
 *
 * <h2>Key Responsibilities:</h2>
 *
 * <ul>
 *   <li>Subscription lifecycle management
 *   <li>Active subscription validation (Policy Layer 1)
 *   <li>Feature toggle management
 *   <li>Trial period tracking
 *   <li>Auto-expiry detection
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriptionService {

  private final SubscriptionRepository subscriptionRepository;
  private final DomainEventPublisher eventPublisher;

  /**
   * Create initial OS subscriptions during tenant onboarding. Used by Auth module
   * (CreateSubscriptionsStep).
   *
   * @param tenantId tenant ID
   * @param selectedOS list of OS codes (e.g. FabricOS, YarnOS); default FabricOS if empty
   * @param trialDays trial period in days
   * @return result with os codes and first subscription trial end
   */
  @Transactional
  public CreateInitialSubscriptionsResult createInitialSubscriptions(
      UUID tenantId, List<String> selectedOS, int trialDays) {
    log.debug("Creating initial subscriptions: tenantId={}, trialDays={}", tenantId, trialDays);

    List<String> osList =
        selectedOS != null && !selectedOS.isEmpty() ? selectedOS : List.of("FabricOS");

    List<Subscription> saved =
        TenantContext.executeInTenantContext(
            tenantId,
            () -> {
              List<Subscription> list = new ArrayList<>();
              for (String osCode : osList) {
                Subscription sub =
                    Subscription.builder()
                        .osCode(osCode)
                        .osName(getOsName(osCode))
                        .status(SubscriptionStatus.TRIAL)
                        .startDate(Instant.now())
                        .trialEndsAt(Instant.now().plus(trialDays, ChronoUnit.DAYS))
                        .features(Map.of())
                        .build();
                sub.setTenantId(tenantId);
                list.add(subscriptionRepository.save(sub));
              }
              return list;
            });

    Instant trialEndsAt = saved.isEmpty() ? null : saved.get(0).getTrialEndsAt();
    List<String> osCodes = saved.stream().map(Subscription::getOsCode).toList();
    log.info("Created {} initial subscriptions: {}", saved.size(), osCodes);
    return CreateInitialSubscriptionsResult.builder()
        .osCodes(osCodes)
        .trialEndsAt(trialEndsAt)
        .build();
  }

  private static String getOsName(String osCode) {
    return switch (osCode) {
      case "FabricOS" -> "Fabric Management Base Platform";
      case "YarnOS" -> "Yarn Production OS";
      case "LoomOS" -> "Weaving Production OS";
      case "KnitOS" -> "Knitting Production OS";
      case "DyeOS" -> "Dyeing & Finishing OS";
      case "AnalyticsOS" -> "Analytics & Reporting OS";
      case "IntelligenceOS" -> "AI & Intelligence OS";
      case "EdgeOS" -> "IoT & Edge Computing OS";
      case "AccountOS" -> "Accounting OS";
      case "CustomOS" -> "Custom Integration OS";
      default -> osCode;
    };
  }

  /**
   * Check if tenant has ACTIVE subscription to given OS.
   *
   * <p>THIS IS POLICY ENGINE LAYER 1 - Most critical check!
   *
   * @param tenantId the tenant ID
   * @param osCode the OS code (YarnOS, LoomOS, etc.)
   * @return true if active subscription exists
   */
  @Transactional(readOnly = true)
  public boolean hasActiveSubscription(UUID tenantId, String osCode) {
    log.debug("Checking active subscription: tenantId={}, osCode={}", tenantId, osCode);

    Optional<Subscription> subscription =
        subscriptionRepository.findActiveSubscriptionByOsCode(tenantId, osCode, Instant.now());

    boolean isActive = subscription.isPresent();
    log.debug("Subscription check result: osCode={}, isActive={}", osCode, isActive);

    return isActive;
  }

  @Transactional(readOnly = true)
  public List<SubscriptionDto> getCompanySubscriptions(UUID companyId) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    log.debug("Getting subscriptions: tenantId={}", tenantId);

    return subscriptionRepository.findByTenantId(tenantId).stream()
        .map(SubscriptionDto::from)
        .toList();
  }

  @Transactional(readOnly = true)
  public List<SubscriptionDto> getActiveSubscriptions() {
    UUID tenantId = TenantContext.getCurrentTenantId();
    log.debug("Getting active subscriptions: tenantId={}", tenantId);

    return subscriptionRepository.findActiveSubscriptions(tenantId, Instant.now()).stream()
        .map(SubscriptionDto::from)
        .toList();
  }

  @Transactional
  public SubscriptionDto activateSubscription(UUID subscriptionId) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    log.info("Activating subscription: tenantId={}, subscriptionId={}", tenantId, subscriptionId);

    Subscription subscription =
        subscriptionRepository
            .findByTenantIdAndId(tenantId, subscriptionId)
            .orElseThrow(() -> new IllegalArgumentException("Subscription not found"));

    subscription.activate();
    Subscription saved = subscriptionRepository.save(subscription);

    eventPublisher.publish(
        new SubscriptionActivatedEvent(
            saved.getTenantId(), saved.getId(), saved.getOsCode(), saved.getOsName()));

    log.info("Subscription activated: id={}, osCode={}", saved.getId(), saved.getOsCode());

    return SubscriptionDto.from(saved);
  }

  @Transactional
  public void expireSubscription(UUID subscriptionId) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    log.info("Expiring subscription: tenantId={}, subscriptionId={}", tenantId, subscriptionId);

    Subscription subscription =
        subscriptionRepository
            .findByTenantIdAndId(tenantId, subscriptionId)
            .orElseThrow(() -> new IllegalArgumentException("Subscription not found"));

    subscription.expire();
    Subscription saved = subscriptionRepository.save(subscription);

    eventPublisher.publish(
        new SubscriptionExpiredEvent(saved.getTenantId(), saved.getId(), saved.getOsCode()));

    log.warn("Subscription expired: id={}, osCode={}", saved.getId(), saved.getOsCode());
  }

  /**
   * Cancel a subscription.
   *
   * <p>Marks subscription as CANCELLED. No reactivation allowed.
   */
  @Transactional
  public SubscriptionDto cancelSubscription(UUID subscriptionId) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    log.info("Cancelling subscription: tenantId={}, subscriptionId={}", tenantId, subscriptionId);

    Subscription subscription =
        subscriptionRepository
            .findByTenantIdAndId(tenantId, subscriptionId)
            .orElseThrow(() -> new IllegalArgumentException("Subscription not found"));

    if (subscription.getStatus() == SubscriptionStatus.CANCELLED) {
      log.warn("Subscription already cancelled: id={}", subscriptionId);
      return SubscriptionDto.from(subscription);
    }

    subscription.cancel();
    Subscription saved = subscriptionRepository.save(subscription);

    log.warn("Subscription cancelled: id={}, osCode={}", saved.getId(), saved.getOsCode());

    return SubscriptionDto.from(saved);
  }

  /** Update subscription (expiry date, features, pricing tier). */
  @Transactional
  public SubscriptionDto updateSubscription(
      UUID subscriptionId, UpdateSubscriptionRequest request) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    log.info("Updating subscription: tenantId={}, subscriptionId={}", tenantId, subscriptionId);

    Subscription subscription =
        subscriptionRepository
            .findByTenantIdAndId(tenantId, subscriptionId)
            .orElseThrow(() -> new IllegalArgumentException("Subscription not found"));

    // Update fields if provided
    if (request.getExpiryDate() != null) {
      subscription.setExpiryDate(request.getExpiryDate());
    }
    if (request.getFeatures() != null) {
      subscription.setFeatures(request.getFeatures());
    }
    if (request.getPricingTier() != null) {
      subscription.setPricingTier(request.getPricingTier());
    }

    Subscription saved = subscriptionRepository.save(subscription);

    log.info("Subscription updated: id={}, osCode={}", saved.getId(), saved.getOsCode());

    return SubscriptionDto.from(saved);
  }

  /** Get subscription by ID. */
  @Transactional(readOnly = true)
  public SubscriptionDto getSubscription(UUID subscriptionId) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    log.debug("Getting subscription: tenantId={}, subscriptionId={}", tenantId, subscriptionId);

    Subscription subscription =
        subscriptionRepository
            .findByTenantIdAndId(tenantId, subscriptionId)
            .orElseThrow(() -> new IllegalArgumentException("Subscription not found"));

    return SubscriptionDto.from(subscription);
  }

  /**
   * Check expiring subscriptions and auto-expire them.
   *
   * <p>Uses batch processing with pagination to avoid memory overflow. Should be called by
   * scheduled job daily.
   *
   * <p><b>Performance Optimization:</b> Processes subscriptions in batches of 100 instead of
   * loading all subscriptions into memory. This prevents system crashes when there are thousands of
   * subscriptions.
   */
  @Transactional
  public void processExpiringSubscriptions() {
    log.info("Processing expiring subscriptions");

    Instant now = Instant.now();
    int pageSize = 100;
    int page = 0;
    int totalExpired = 0;

    Page<Subscription> subscriptionPage;
    do {
      Pageable pageable = PageRequest.of(page, pageSize);
      subscriptionPage = subscriptionRepository.findExpiredButNotExpiredStatus(now, pageable);

      List<Subscription> toExpire = subscriptionPage.getContent();

      if (toExpire.isEmpty()) {
        break;
      }

      // Batch update all expired subscriptions
      for (Subscription subscription : toExpire) {
        log.warn(
            "Auto-expiring subscription: id={}, osCode={}, tenantId={}",
            subscription.getId(),
            subscription.getOsCode(),
            subscription.getTenantId());

        subscription.expire();
      }

      // Batch save - more efficient than individual saves
      subscriptionRepository.saveAll(toExpire);

      // Publish events for all expired subscriptions
      for (Subscription subscription : toExpire) {
        eventPublisher.publish(
            new SubscriptionExpiredEvent(
                subscription.getTenantId(), subscription.getId(), subscription.getOsCode()));
      }

      totalExpired += toExpire.size();
      page++;

      log.debug(
          "Processed batch {}/{}: expired {} subscriptions",
          page,
          subscriptionPage.getTotalPages(),
          toExpire.size());

    } while (subscriptionPage.hasNext());

    log.info("Finished processing expiring subscriptions. Total expired: {}", totalExpired);
  }
}
