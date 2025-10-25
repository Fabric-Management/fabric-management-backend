package com.fabricmanagement.common.platform.company.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Subscription usage quota tracking.
 *
 * <p>Tracks usage limits for various resources (users, API calls, storage, etc.)
 * per subscription. This is Layer 3 of the Policy Engine.</p>
 *
 * <h2>Quota Types:</h2>
 * <ul>
 *   <li><strong>users</strong> - Number of active users</li>
 *   <li><strong>api_calls</strong> - API requests per month</li>
 *   <li><strong>storage_gb</strong> - Storage space in GB</li>
 *   <li><strong>fiber_entities</strong> - Number of fiber entities (YarnOS)</li>
 *   <li><strong>yarn_skus</strong> - Number of yarn SKUs (YarnOS)</li>
 *   <li><strong>iot_devices</strong> - Number of IoT devices (EdgeOS)</li>
 *   <li><strong>integrations</strong> - Number of active integrations (CustomOS)</li>
 * </ul>
 *
 * <h2>Reset Periods:</h2>
 * <ul>
 *   <li><strong>NONE</strong> - Static limit, never resets (e.g., users, storage)</li>
 *   <li><strong>MONTHLY</strong> - Resets on 1st of each month (e.g., API calls)</li>
 *   <li><strong>DAILY</strong> - Resets daily (rare, for rate limiting)</li>
 * </ul>
 *
 * <h2>Example:</h2>
 * <pre>{@code
 * SubscriptionQuota apiQuota = SubscriptionQuota.builder()
 *     .subscriptionId(yarnOsSubscriptionId)
 *     .quotaType("api_calls")
 *     .quotaLimit(100_000L)
 *     .quotaUsed(45_230L)
 *     .resetPeriod("MONTHLY")
 *     .build();
 *
 * if (apiQuota.isExceeded()) {
 *     throw new QuotaExceededException("API call limit reached");
 * }
 * }</pre>
 */
@Entity
@Table(name = "common_subscription_quota", schema = "common_company",
    indexes = {
        @Index(name = "idx_quota_tenant_type", columnList = "tenant_id,quota_type"),
        @Index(name = "idx_quota_subscription", columnList = "subscription_id"),
        @Index(name = "idx_quota_reset_period", columnList = "reset_period,last_reset_at")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_tenant_subscription_quota",
            columnNames = {"tenant_id", "subscription_id", "quota_type"})
    })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionQuota extends BaseEntity {

    /**
     * Reference to the subscription this quota belongs to.
     */
    @Column(name = "subscription_id", nullable = false)
    private UUID subscriptionId;

    /**
     * Type of quota being tracked.
     *
     * <p>Examples: "users", "api_calls", "storage_gb", "fiber_entities"</p>
     */
    @Column(name = "quota_type", nullable = false, length = 50)
    private String quotaType;

    /**
     * Maximum allowed value for this quota.
     *
     * <p>Use {@code Long.MAX_VALUE} for "unlimited" quotas.</p>
     */
    @Column(name = "quota_limit", nullable = false)
    private Long quotaLimit;

    /**
     * Current usage of this quota.
     *
     * <p>Incremented as resources are consumed, reset based on {@link #resetPeriod}.</p>
     */
    @Column(name = "quota_used", nullable = false)
    @Builder.Default
    private Long quotaUsed = 0L;

    /**
     * How often this quota resets.
     *
     * <p>Values: "NONE", "MONTHLY", "DAILY"</p>
     */
    @Column(name = "reset_period", length = 20)
    private String resetPeriod;

    /**
     * Timestamp of the last quota reset.
     *
     * <p>Only applicable for MONTHLY/DAILY quotas.</p>
     */
    @Column(name = "last_reset_at")
    private Instant lastResetAt;

    // ==================== Business Logic ====================

    /**
     * Check if quota has been exceeded.
     *
     * @return true if quota_used >= quota_limit
     */
    public boolean isExceeded() {
        return quotaUsed >= quotaLimit;
    }

    /**
     * Check if quota is unlimited.
     *
     * @return true if quota_limit is set to max value
     */
    public boolean isUnlimited() {
        return quotaLimit.equals(Long.MAX_VALUE);
    }

    /**
     * Get remaining quota.
     *
     * @return number of units remaining (0 if exceeded)
     */
    public long remaining() {
        return Math.max(0, quotaLimit - quotaUsed);
    }

    /**
     * Get usage percentage.
     *
     * @return percentage used (0-100)
     */
    public double usagePercentage() {
        if (quotaLimit == 0 || isUnlimited()) {
            return 0.0;
        }
        return (quotaUsed * 100.0) / quotaLimit;
    }

    /**
     * Increment quota usage by given amount.
     *
     * @param amount the amount to increment
     * @throws IllegalArgumentException if amount is negative
     */
    public void increment(long amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("Increment amount cannot be negative");
        }
        this.quotaUsed += amount;
    }

    /**
     * Decrement quota usage (e.g., when resource is deleted).
     *
     * @param amount the amount to decrement
     */
    public void decrement(long amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("Decrement amount cannot be negative");
        }
        this.quotaUsed = Math.max(0, this.quotaUsed - amount);
    }

    /**
     * Reset quota usage to 0.
     *
     * <p>Updates {@link #lastResetAt} to current time.</p>
     */
    public void reset() {
        this.quotaUsed = 0L;
        this.lastResetAt = Instant.now();
    }

    /**
     * Check if quota needs to be reset based on reset period.
     *
     * @return true if reset is due
     */
    public boolean needsReset() {
        if ("NONE".equals(resetPeriod) || lastResetAt == null) {
            return false;
        }

        Instant now = Instant.now();

        if ("DAILY".equals(resetPeriod)) {
            // Check if more than 24 hours since last reset
            return now.isAfter(lastResetAt.plusSeconds(24 * 3600));
        }

        if ("MONTHLY".equals(resetPeriod)) {
            // Check if we're in a new month
            // Simple check: more than 28 days (will be refined by scheduled job)
            return now.isAfter(lastResetAt.plusSeconds(28 * 24 * 3600));
        }

        return false;
    }

    /**
     * Validate quota before persisting.
     */
    @PrePersist
    @PreUpdate
    private void validate() {
        if (quotaLimit < 0) {
            throw new IllegalStateException("Quota limit cannot be negative");
        }
        if (quotaUsed < 0) {
            throw new IllegalStateException("Quota used cannot be negative");
        }
    }

    @Override
    protected String getModuleCode() {
        return "QUOTA";
    }
}

