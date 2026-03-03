package com.fabricmanagement.common.platform.user.app;

import com.fabricmanagement.common.platform.user.domain.event.UserCreatedEvent;
import com.fabricmanagement.common.platform.user.domain.event.UserDeactivatedEvent;
import com.fabricmanagement.common.platform.user.domain.event.UserProfileUpdatedEvent;
import com.fabricmanagement.human.core.employee.domain.event.EmployeeUpdatedEvent;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

/**
 * Invalidates user caches when user-related domain events occur.
 *
 * <p>Listens to UserCreatedEvent, UserDeactivatedEvent, UserProfileUpdatedEvent, and
 * EmployeeUpdatedEvent. Evicts users-by-tenant and users-by-company caches so query results
 * (including enriched Employee data) stay consistent.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserCacheInvalidationService {

  private final CacheManager cacheManager;

  private static final String CACHE_USERS_BY_TENANT = "users-by-tenant";
  private static final String CACHE_USERS_BY_COMPANY = "users-by-company";

  @EventListener
  public void onUserCreated(UserCreatedEvent event) {
    evictUserCaches(event.getTenantId(), event.getCompanyId());
  }

  @EventListener
  public void onUserDeactivated(UserDeactivatedEvent event) {
    evictTenantCache(event.getTenantId());
  }

  @EventListener
  public void onUserProfileUpdated(UserProfileUpdatedEvent event) {
    evictTenantCache(event.getTenantId());
  }

  @EventListener
  public void onEmployeeUpdated(EmployeeUpdatedEvent event) {
    evictTenantCache(event.getTenantId());
  }

  private void evictUserCaches(UUID tenantId, UUID companyId) {
    evictTenantCache(tenantId);
    if (companyId != null) {
      evictCompanyCache(tenantId, companyId);
    }
  }

  private void evictTenantCache(UUID tenantId) {
    var cache = cacheManager.getCache(CACHE_USERS_BY_TENANT);
    if (cache != null) {
      cache.evict(tenantId != null ? tenantId.toString() : null);
      log.trace("Evicted cache {} for tenantId={}", CACHE_USERS_BY_TENANT, tenantId);
    }
  }

  private void evictCompanyCache(UUID tenantId, UUID companyId) {
    var cache = cacheManager.getCache(CACHE_USERS_BY_COMPANY);
    if (cache != null) {
      String key =
          (tenantId != null ? tenantId.toString() : "")
              + "-"
              + (companyId != null ? companyId.toString() : "");
      cache.evict(key);
      log.trace("Evicted cache {} for key={}", CACHE_USERS_BY_COMPANY, key);
    }
  }
}
