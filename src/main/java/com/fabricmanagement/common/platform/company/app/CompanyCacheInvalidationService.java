package com.fabricmanagement.common.platform.company.app;

import com.fabricmanagement.common.platform.company.domain.event.CompanyCreatedEvent;
import com.fabricmanagement.common.platform.company.domain.event.CompanyUpdatedEvent;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

/**
 * Invalidates company caches when company-related domain events occur.
 *
 * <p>Listens to CompanyCreatedEvent and CompanyUpdatedEvent and evicts companies-by-tenant cache.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CompanyCacheInvalidationService {

  private static final String CACHE_COMPANIES_BY_TENANT = "companies-by-tenant";

  private final CacheManager cacheManager;

  @EventListener
  public void onCompanyCreated(CompanyCreatedEvent event) {
    evictCaches(event.getTenantId());
  }

  @EventListener
  public void onCompanyUpdated(CompanyUpdatedEvent event) {
    evictCaches(event.getTenantId());
  }

  private void evictCaches(UUID tenantId) {
    var cache = cacheManager.getCache(CACHE_COMPANIES_BY_TENANT);
    if (cache != null) {
      cache.evict(tenantId != null ? tenantId.toString() : null);
      log.trace("Evicted cache {} for tenantId={}", CACHE_COMPANIES_BY_TENANT, tenantId);
    }
  }
}
