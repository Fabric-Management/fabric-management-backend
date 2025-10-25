package com.fabricmanagement.common.platform.company.dto;

import com.fabricmanagement.common.platform.company.domain.Subscription;
import com.fabricmanagement.common.platform.company.domain.SubscriptionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionDto {

    private UUID id;
    private UUID tenantId;
    private String uid;
    private String osCode;
    private String osName;
    private SubscriptionStatus status;
    private Instant startDate;
    private Instant expiryDate;
    private Instant trialEndsAt;
    private Map<String, Boolean> features;
    
    /**
     * Pricing tier for this subscription.
     *
     * <p>Tier names vary by OS:</p>
     * <ul>
     *   <li>YarnOS, LoomOS, etc.: "Starter", "Professional", "Enterprise"</li>
     *   <li>AnalyticsOS: "Standard", "Advanced", "Enterprise"</li>
     *   <li>IntelligenceOS: "Professional", "Enterprise"</li>
     * </ul>
     */
    private String pricingTier;
    
    private Boolean isActive;
    private Instant createdAt;

    public static SubscriptionDto from(Subscription subscription) {
        return SubscriptionDto.builder()
            .id(subscription.getId())
            .tenantId(subscription.getTenantId())
            .uid(subscription.getUid())
            .osCode(subscription.getOsCode())
            .osName(subscription.getOsName())
            .status(subscription.getStatus())
            .startDate(subscription.getStartDate())
            .expiryDate(subscription.getExpiryDate())
            .trialEndsAt(subscription.getTrialEndsAt())
            .features(subscription.getFeatures())
            .pricingTier(subscription.getPricingTier())
            .isActive(subscription.isActive())
            .createdAt(subscription.getCreatedAt())
            .build();
    }
}

