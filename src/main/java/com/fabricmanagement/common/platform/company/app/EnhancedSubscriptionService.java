package com.fabricmanagement.common.platform.company.app;

import com.fabricmanagement.common.platform.company.domain.*;
import com.fabricmanagement.common.platform.company.domain.exception.FeatureNotAvailableException;
import com.fabricmanagement.common.platform.company.domain.exception.QuotaExceededException;
import com.fabricmanagement.common.platform.company.domain.exception.SubscriptionRequiredException;
import com.fabricmanagement.common.platform.company.infra.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Enhanced Subscription Service - 4-Layer Access Control.
 *
 * <p><b>CRITICAL FOR POLICY ENGINE!</b></p>
 *
 * <h2>4-Layer Architecture:</h2>
 * <pre>
 * ┌───────────────────────────────────────┐
 * │ Layer 1: OS Subscription Check        │  ← Has YarnOS subscription?
 * ├───────────────────────────────────────┤
 * │ Layer 2: Feature Entitlement Check    │  ← Has yarn.blend.management feature?
 * ├───────────────────────────────────────┤
 * │ Layer 3: Usage Quota Check            │  ← Within API call limit?
 * ├───────────────────────────────────────┤
 * │ Layer 4: Policy Engine (RBAC/ABAC)    │  ← User has permission? (handled by Policy module)
 * └───────────────────────────────────────┘
 * </pre>
 *
 * <p>This service handles Layers 1-3. Layer 4 is handled by common/platform/policy.</p>
 *
 * <h2>Usage in Controllers:</h2>
 * <pre>{@code
 * @PostMapping("/blend")
 * public ResponseEntity<?> createBlend(@RequestBody CreateBlendRequest request) {
 *     UUID tenantId = TenantContext.getCurrentTenantId();
 *
 *     // 4-Layer check
 *     subscriptionService.enforceEntitlement(
 *         tenantId,
 *         "yarn.blend.management",  // Feature ID
 *         "fiber_entities"          // Quota type (optional)
 *     );
 *
 *     // Proceed with business logic...
 * }
 * }</pre>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EnhancedSubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final FeatureCatalogRepository featureCatalogRepository;
    private final SubscriptionQuotaRepository quotaRepository;

    /**
     * LAYER 1: Check if tenant has active OS subscription.
     *
     * @param tenantId the tenant ID
     * @param osCode the OS code (e.g., "YarnOS")
     * @return true if active subscription exists
     */
    @Cacheable(value = "subscription-check", key = "#tenantId + ':' + #osCode")
    @Transactional(readOnly = true)
    public boolean hasActiveSubscription(UUID tenantId, String osCode) {
        log.debug("[Layer 1] Checking OS subscription: tenantId={}, osCode={}", tenantId, osCode);

        Optional<Subscription> subscription = subscriptionRepository
            .findActiveSubscriptionByOsCode(tenantId, osCode, Instant.now());

        boolean hasSubscription = subscription.isPresent();
        log.debug("[Layer 1] Result: {}", hasSubscription ? "PASS" : "FAIL - No active subscription");

        return hasSubscription;
    }

    /**
     * LAYER 2: Check if tenant has access to specific feature.
     *
     * <p>Checks both OS subscription AND feature entitlement in current tier.</p>
     *
     * @param tenantId the tenant ID
     * @param featureId the feature ID (e.g., "yarn.blend.management")
     * @return true if feature is available
     */
    @Cacheable(value = "feature-check", key = "#tenantId + ':' + #featureId")
    @Transactional(readOnly = true)
    public boolean hasFeature(UUID tenantId, String featureId) {
        log.debug("[Layer 2] Checking feature entitlement: tenantId={}, featureId={}", tenantId, featureId);

        String osCode = extractOsCode(featureId);

        Optional<Subscription> subscription = subscriptionRepository
            .findActiveSubscriptionByOsCode(tenantId, osCode, Instant.now());

        if (subscription.isEmpty()) {
            log.debug("[Layer 2] Result: FAIL - No active subscription to {}", osCode);
            return false;
        }

        Optional<FeatureCatalog> feature = featureCatalogRepository.findByFeatureId(featureId);

        if (feature.isEmpty()) {
            log.warn("[Layer 2] Feature not found in catalog: {}", featureId);
            return false;
        }

        boolean hasFeature = feature.get().isAvailableInTier(subscription.get().getPricingTier());

        log.debug("[Layer 2] Result: {} - Tier: {}, Required: {}",
            hasFeature ? "PASS" : "FAIL",
            subscription.get().getPricingTier(),
            feature.get().getMinimumTier());

        return hasFeature;
    }

    /**
     * LAYER 3: Check if tenant is within usage quota.
     *
     * @param tenantId the tenant ID
     * @param quotaType the quota type (e.g., "api_calls", "fiber_entities")
     * @return true if within quota
     */
    @Transactional(readOnly = true)
    public boolean isWithinQuota(UUID tenantId, String quotaType) {
        log.debug("[Layer 3] Checking quota: tenantId={}, quotaType={}", tenantId, quotaType);

        Optional<SubscriptionQuota> quota = quotaRepository
            .findByTenantIdAndQuotaType(tenantId, quotaType);

        if (quota.isEmpty()) {
            log.debug("[Layer 3] No quota configured for {} - allowing", quotaType);
            return true; // No quota = unlimited
        }

        boolean withinQuota = !quota.get().isExceeded();

        log.debug("[Layer 3] Result: {} - Used: {}/{}", 
            withinQuota ? "PASS" : "FAIL", 
            quota.get().getQuotaUsed(), 
            quota.get().getQuotaLimit());

        return withinQuota;
    }

    /**
     * Complete 4-layer entitlement enforcement.
     *
     * <p>Throws exception if any layer fails.</p>
     *
     * <p><b>Usage in controllers:</b></p>
     * <pre>{@code
     * subscriptionService.enforceEntitlement(tenantId, "yarn.blend.management", "fiber_entities");
     * }</pre>
     *
     * @param tenantId the tenant ID
     * @param featureId the feature ID to check
     * @param quotaType the quota type to check (optional, can be null)
     * @throws SubscriptionRequiredException if no OS subscription
     * @throws FeatureNotAvailableException if feature not in current tier
     * @throws QuotaExceededException if usage quota exceeded
     */
    @Transactional(readOnly = true)
    public void enforceEntitlement(UUID tenantId, String featureId, String quotaType) {
        log.info("[Entitlement Check] featureId={}, quotaType={}", featureId, quotaType);

        // Layer 1: OS Subscription
        String osCode = extractOsCode(featureId);
        if (!hasActiveSubscription(tenantId, osCode)) {
            log.warn("[Entitlement Check] DENIED - No active subscription to {}", osCode);
            throw new SubscriptionRequiredException(
                String.format("Active subscription to %s is required for this feature", osCode),
                osCode
            );
        }

        // Layer 2: Feature Entitlement
        if (!hasFeature(tenantId, featureId)) {
            Optional<Subscription> sub = subscriptionRepository
                .findActiveSubscriptionByOsCode(tenantId, osCode, Instant.now());
            
            Optional<FeatureCatalog> feature = featureCatalogRepository.findByFeatureId(featureId);
            
            String currentTier = sub.map(Subscription::getPricingTier).orElse("Unknown");
            String requiredTier = feature.map(FeatureCatalog::getMinimumTier).orElse("Unknown");

            log.warn("[Entitlement Check] DENIED - Feature not available. Current: {}, Required: {}",
                currentTier, requiredTier);

            throw new FeatureNotAvailableException(
                String.format("Feature '%s' is not available in your current tier (%s). Upgrade to %s or higher.",
                    featureId, currentTier, requiredTier),
                featureId,
                requiredTier
            );
        }

        // Layer 3: Usage Quota (if specified)
        if (quotaType != null && !quotaType.isBlank()) {
            if (!isWithinQuota(tenantId, quotaType)) {
                Optional<SubscriptionQuota> quota = quotaRepository
                    .findByTenantIdAndQuotaType(tenantId, quotaType);

                Long used = quota.map(SubscriptionQuota::getQuotaUsed).orElse(0L);
                Long limit = quota.map(SubscriptionQuota::getQuotaLimit).orElse(0L);

                log.warn("[Entitlement Check] DENIED - Quota exceeded: {}/{}", used, limit);

                throw new QuotaExceededException(
                    String.format("Quota exceeded for %s. Used: %d/%d. Please upgrade your plan.",
                        quotaType, used, limit),
                    quotaType,
                    limit,
                    used
                );
            }
        }

        log.info("[Entitlement Check] GRANTED - All layers passed");
    }

    /**
     * Increment quota usage.
     *
     * <p>Call this after successful operation to track usage.</p>
     *
     * @param tenantId the tenant ID
     * @param quotaType the quota type
     * @param increment the amount to increment (default: 1)
     */
    @Transactional
    public void incrementQuota(UUID tenantId, String quotaType, long increment) {
        log.debug("Incrementing quota: tenantId={}, quotaType={}, increment={}", 
            tenantId, quotaType, increment);

        Optional<SubscriptionQuota> quota = quotaRepository
            .findByTenantIdAndQuotaType(tenantId, quotaType);

        if (quota.isPresent()) {
            quota.get().increment(increment);
            quotaRepository.save(quota.get());

            log.debug("Quota incremented: {}/{}", quota.get().getQuotaUsed(), quota.get().getQuotaLimit());
        } else {
            log.debug("No quota configured for {} - skipping increment", quotaType);
        }
    }

    /**
     * Extract OS code from feature ID.
     *
     * <p>Feature ID format: {os_prefix}.{module}.{feature_name}</p>
     * <p>Example: "yarn.blend.management" → "YarnOS"</p>
     *
     * @param featureId the feature ID
     * @return OS code
     */
    private String extractOsCode(String featureId) {
        if (featureId == null || !featureId.contains(".")) {
            throw new IllegalArgumentException("Invalid feature ID format: " + featureId);
        }

        String prefix = featureId.split("\\.")[0];

        return switch (prefix.toLowerCase()) {
            case "yarn" -> "YarnOS";
            case "weaving", "loom" -> "LoomOS";
            case "knit" -> "KnitOS";
            case "dye", "finish" -> "DyeOS";
            case "analytics" -> "AnalyticsOS";
            case "intelligence", "ai" -> "IntelligenceOS";
            case "edge", "iot" -> "EdgeOS";
            case "account", "accounting" -> "AccountOS";
            case "custom", "integration" -> "CustomOS";
            default -> "FabricOS"; // Fallback to base OS
        };
    }
}

