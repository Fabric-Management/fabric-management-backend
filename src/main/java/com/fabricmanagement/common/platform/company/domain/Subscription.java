package com.fabricmanagement.common.platform.company.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Type;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * OS Subscription entity.
 *
 * <p>Represents a tenant's subscription to an Operating Subscription (OS).
 * This is the foundation of Layer 1 in the 5-layer Policy Engine.</p>
 *
 * <h2>Policy Engine Integration:</h2>
 * <p>Before ANY operation, the policy engine checks if the tenant has
 * an ACTIVE subscription to the required OS. No subscription = Access DENIED.</p>
 *
 * <h2>Lifecycle:</h2>
 * <pre>
 * TRIAL (14-30 days) → ACTIVE (paid) → EXPIRED (end date)
 *    ↓                    ↓                ↓
 * EXPIRED            SUSPENDED         CANCELLED
 * </pre>
 *
 * <h2>Example:</h2>
 * <pre>{@code
 * Subscription yarnOS = Subscription.builder()
 *     .osCode("YarnOS")
 *     .osName("Yarn Production OS")
 *     .status(SubscriptionStatus.TRIAL)
 *     .startDate(Instant.now())
 *     .trialEndsAt(Instant.now().plus(14, ChronoUnit.DAYS))
 *     .pricingTier("Professional")
 *     .build();
 * }</pre>
 *
 * <h2>Pricing Tier Examples by OS:</h2>
 * <ul>
 *   <li>YarnOS: "Starter", "Professional", "Enterprise"</li>
 *   <li>AnalyticsOS: "Standard", "Advanced", "Enterprise"</li>
 *   <li>IntelligenceOS: "Professional", "Enterprise"</li>
 *   <li>EdgeOS: "Starter", "Professional", "Enterprise"</li>
 * </ul>
 */
@Entity
@Table(name = "common_subscription", schema = "common_company",
    indexes = {
        @Index(name = "idx_subscription_tenant_os", columnList = "tenant_id,os_code"),
        @Index(name = "idx_subscription_status", columnList = "status")
    })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Subscription extends BaseEntity {

    @Column(name = "os_code", nullable = false, length = 50)
    private String osCode;

    @Column(name = "os_name", nullable = false, length = 255)
    private String osName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private SubscriptionStatus status = SubscriptionStatus.TRIAL;

    @Column(name = "start_date", nullable = false)
    private Instant startDate;

    @Column(name = "expiry_date")
    private Instant expiryDate;

    @Column(name = "trial_ends_at")
    private Instant trialEndsAt;

    @Type(JsonType.class)
    @Column(name = "features", columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Boolean> features = new HashMap<>();

    /**
     * Pricing tier for this subscription.
     *
     * <p>Tier names vary by OS:</p>
     * <ul>
     *   <li>YarnOS, LoomOS, KnitOS, DyeOS, EdgeOS: "Starter", "Professional", "Enterprise"</li>
     *   <li>AnalyticsOS, AccountOS, CustomOS: "Standard", "Professional", "Enterprise"</li>
     *   <li>IntelligenceOS: "Professional", "Enterprise"</li>
     * </ul>
     */
    @Column(name = "pricing_tier", nullable = false, length = 50)
    private String pricingTier;

    public boolean isActive() {
        if (this.status == SubscriptionStatus.ACTIVE) {
            if (this.expiryDate == null) {
                return true;
            }
            return this.expiryDate.isAfter(Instant.now());
        }

        if (this.status == SubscriptionStatus.TRIAL) {
            return this.trialEndsAt != null && this.trialEndsAt.isAfter(Instant.now());
        }

        return false;
    }

    public boolean isExpired() {
        return this.status == SubscriptionStatus.EXPIRED || 
               (this.expiryDate != null && this.expiryDate.isBefore(Instant.now())) ||
               (this.trialEndsAt != null && this.trialEndsAt.isBefore(Instant.now()));
    }

    public void activate() {
        this.status = SubscriptionStatus.ACTIVE;
    }

    public void expire() {
        this.status = SubscriptionStatus.EXPIRED;
    }

    public void cancel() {
        this.status = SubscriptionStatus.CANCELLED;
    }

    public void suspend() {
        this.status = SubscriptionStatus.SUSPENDED;
    }

    public void resume() {
        if (this.status == SubscriptionStatus.SUSPENDED) {
            this.status = SubscriptionStatus.ACTIVE;
        }
    }

    public boolean hasFeature(String featureName) {
        return features.getOrDefault(featureName, false);
    }

    public void enableFeature(String featureName) {
        this.features.put(featureName, true);
    }

    public void disableFeature(String featureName) {
        this.features.put(featureName, false);
    }

    @Override
    protected String getModuleCode() {
        return "SUB";
    }

    /**
     * Validate subscription before persisting.
     */
    @PrePersist
    @PreUpdate
    private void validate() {
        // Validate pricing tier for this OS
        if (osCode != null && pricingTier != null) {
            if (!PricingTierValidator.isValidTier(osCode, pricingTier)) {
                throw new IllegalStateException(
                    String.format("Invalid pricing tier '%s' for OS '%s'. Valid tiers: %s",
                        pricingTier, osCode, PricingTierValidator.getValidTiers(osCode))
                );
            }
        }

        // Set default tier if not specified
        if (pricingTier == null && osCode != null) {
            this.pricingTier = PricingTierValidator.getDefaultTier(osCode);
        }
    }
}

