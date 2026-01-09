package com.fabricmanagement.common.platform.company.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.platform.company.domain.SubscriptionQuota;
import com.fabricmanagement.common.platform.company.dto.SubscriptionQuotaDto;
import com.fabricmanagement.common.platform.company.infra.repository.SubscriptionQuotaRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing subscription quotas.
 *
 * <p>Handles quota tracking, reset, and retrieval for subscriptions.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriptionQuotaService {

  private final SubscriptionQuotaRepository quotaRepository;

  /** Get all quotas for a subscription. */
  @Transactional(readOnly = true)
  public List<SubscriptionQuotaDto> getSubscriptionQuotas(UUID subscriptionId) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    log.debug(
        "Getting quotas for subscription: tenantId={}, subscriptionId={}",
        tenantId,
        subscriptionId);

    List<SubscriptionQuota> quotas = quotaRepository.findBySubscriptionId(subscriptionId);

    return quotas.stream().map(SubscriptionQuotaDto::from).toList();
  }

  /** Get all quotas for current tenant. */
  @Transactional(readOnly = true)
  public List<SubscriptionQuotaDto> getTenantQuotas() {
    UUID tenantId = TenantContext.getCurrentTenantId();
    log.debug("Getting quotas for tenant: tenantId={}", tenantId);

    List<SubscriptionQuota> quotas = quotaRepository.findByTenantId(tenantId);

    return quotas.stream().map(SubscriptionQuotaDto::from).toList();
  }

  /** Reset quota for a specific type. */
  @Transactional
  public SubscriptionQuotaDto resetQuota(UUID subscriptionId, String quotaType) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    log.info(
        "Resetting quota: tenantId={}, subscriptionId={}, quotaType={}",
        tenantId,
        subscriptionId,
        quotaType);

    SubscriptionQuota quota =
        quotaRepository.findBySubscriptionId(subscriptionId).stream()
            .filter(q -> q.getQuotaType().equals(quotaType))
            .findFirst()
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "Quota not found: subscriptionId="
                            + subscriptionId
                            + ", quotaType="
                            + quotaType));

    // Validate tenant ownership
    if (!quota.getTenantId().equals(tenantId)) {
      throw new IllegalArgumentException("Quota does not belong to current tenant");
    }

    quota.reset();
    SubscriptionQuota saved = quotaRepository.save(quota);

    log.info(
        "Quota reset: subscriptionId={}, quotaType={}, used={}",
        saved.getSubscriptionId(),
        saved.getQuotaType(),
        saved.getQuotaUsed());

    return SubscriptionQuotaDto.from(saved);
  }
}
