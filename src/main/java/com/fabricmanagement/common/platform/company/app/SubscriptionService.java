package com.fabricmanagement.common.platform.company.app;

import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.platform.company.domain.Subscription;
import com.fabricmanagement.common.platform.company.domain.SubscriptionStatus;
import com.fabricmanagement.common.platform.company.domain.event.SubscriptionActivatedEvent;
import com.fabricmanagement.common.platform.company.domain.event.SubscriptionExpiredEvent;
import com.fabricmanagement.common.platform.company.dto.SubscriptionDto;
import com.fabricmanagement.common.platform.company.infra.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Subscription Service - CRITICAL for Policy Engine Layer 1.
 *
 * <p>Manages OS subscriptions which gate access to all system features.
 * Before any operation, Policy Engine checks: "Does tenant have active OS subscription?"</p>
 *
 * <h2>Key Responsibilities:</h2>
 * <ul>
 *   <li>Subscription lifecycle management</li>
 *   <li>Active subscription validation (Policy Layer 1)</li>
 *   <li>Feature toggle management</li>
 *   <li>Trial period tracking</li>
 *   <li>Auto-expiry detection</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final DomainEventPublisher eventPublisher;

    /**
     * Check if tenant has ACTIVE subscription to given OS.
     *
     * <p>THIS IS POLICY ENGINE LAYER 1 - Most critical check!</p>
     *
     * @param tenantId the tenant ID
     * @param osCode the OS code (YarnOS, LoomOS, etc.)
     * @return true if active subscription exists
     */
    @Transactional(readOnly = true)
    public boolean hasActiveSubscription(UUID tenantId, String osCode) {
        log.debug("Checking active subscription: tenantId={}, osCode={}", tenantId, osCode);

        Optional<Subscription> subscription = subscriptionRepository
            .findActiveSubscriptionByOsCode(tenantId, osCode, Instant.now());

        boolean isActive = subscription.isPresent();
        log.debug("Subscription check result: osCode={}, isActive={}", osCode, isActive);

        return isActive;
    }

    @Transactional(readOnly = true)
    public List<SubscriptionDto> getCompanySubscriptions(UUID companyId) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        log.debug("Getting subscriptions: tenantId={}", tenantId);

        return subscriptionRepository.findByTenantId(tenantId)
            .stream()
            .map(SubscriptionDto::from)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<SubscriptionDto> getActiveSubscriptions() {
        UUID tenantId = TenantContext.getCurrentTenantId();
        log.debug("Getting active subscriptions: tenantId={}", tenantId);

        return subscriptionRepository.findActiveSubscriptions(tenantId, Instant.now())
            .stream()
            .map(SubscriptionDto::from)
            .toList();
    }

    @Transactional
    public SubscriptionDto activateSubscription(UUID subscriptionId) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        log.info("Activating subscription: tenantId={}, subscriptionId={}", tenantId, subscriptionId);

        Subscription subscription = subscriptionRepository.findByTenantIdAndId(tenantId, subscriptionId)
            .orElseThrow(() -> new IllegalArgumentException("Subscription not found"));

        subscription.activate();
        Subscription saved = subscriptionRepository.save(subscription);

        eventPublisher.publish(new SubscriptionActivatedEvent(
            saved.getTenantId(),
            saved.getId(),
            saved.getOsCode(),
            saved.getOsName()
        ));

        log.info("Subscription activated: id={}, osCode={}", saved.getId(), saved.getOsCode());

        return SubscriptionDto.from(saved);
    }

    @Transactional
    public void expireSubscription(UUID subscriptionId) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        log.info("Expiring subscription: tenantId={}, subscriptionId={}", tenantId, subscriptionId);

        Subscription subscription = subscriptionRepository.findByTenantIdAndId(tenantId, subscriptionId)
            .orElseThrow(() -> new IllegalArgumentException("Subscription not found"));

        subscription.expire();
        Subscription saved = subscriptionRepository.save(subscription);

        eventPublisher.publish(new SubscriptionExpiredEvent(
            saved.getTenantId(),
            saved.getId(),
            saved.getOsCode()
        ));

        log.warn("Subscription expired: id={}, osCode={}", saved.getId(), saved.getOsCode());
    }

    /**
     * Check expiring subscriptions and auto-expire them.
     * 
     * <p>Should be called by scheduled job daily.</p>
     */
    @Transactional
    public void processExpiringSubscriptions() {
        log.info("Processing expiring subscriptions");

        List<Subscription> allSubscriptions = subscriptionRepository.findAll();

        for (Subscription subscription : allSubscriptions) {
            if (subscription.isExpired() && subscription.getStatus() != SubscriptionStatus.EXPIRED) {
                log.warn("Auto-expiring subscription: id={}, osCode={}", 
                    subscription.getId(), subscription.getOsCode());
                
                subscription.expire();
                subscriptionRepository.save(subscription);

                eventPublisher.publish(new SubscriptionExpiredEvent(
                    subscription.getTenantId(),
                    subscription.getId(),
                    subscription.getOsCode()
                ));
            }
        }

        log.info("Finished processing expiring subscriptions");
    }
}

