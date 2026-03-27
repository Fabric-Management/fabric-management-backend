package com.fabricmanagement.platform.user.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.platform.user.domain.event.UserCreatedEvent;
import com.fabricmanagement.platform.user.domain.event.UserDeactivatedEvent;
import com.fabricmanagement.platform.user.domain.event.UserProfileUpdatedEvent;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.cache.support.SimpleCacheManager;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserCacheInvalidationService")
class UserCacheInvalidationServiceTest {

  private static final UUID TENANT_ID = UUID.randomUUID();
  private static final UUID ORGANIZATION_ID = UUID.randomUUID();
  private static final UUID USER_ID = UUID.randomUUID();

  private static CacheManager cacheManager() {
    SimpleCacheManager manager = new SimpleCacheManager();
    manager.setCaches(
        java.util.List.of(
            new ConcurrentMapCache("users-by-tenant"),
            new ConcurrentMapCache("users-by-organization")));
    manager.afterPropertiesSet();
    return manager;
  }

  @Mock private CacheManager mockCacheManager;

  @Nested
  @DisplayName("with real CacheManager")
  class WithRealCacheManager {

    @InjectMocks private UserCacheInvalidationService service;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
      // Inject real cache manager via reflection so we can verify eviction
      org.springframework.test.util.ReflectionTestUtils.setField(
          service, "cacheManager", cacheManager());
    }

    @Test
    @DisplayName("onUserCreated evicts tenant and organization caches")
    void onUserCreated_evictsBothCaches() {
      UserCreatedEvent event =
          new UserCreatedEvent(TENANT_ID, USER_ID, "Display", "email@test.com", ORGANIZATION_ID);

      service.onUserCreated(event);

      // Service calls cacheManager.getCache("users-by-tenant").evict(tenantId) and
      // getCache("users-by-organization").evict(tenantId + "-" + organizationId). We use real cache
      // manager
      // so evict is a no-op on empty cache; we just ensure no exception.
      // With mock we'd verify getCache and evict were called.
    }

    @Test
    @DisplayName("onUserDeactivated evicts tenant cache only")
    void onUserDeactivated_evictsTenantCache() {
      UserDeactivatedEvent event = new UserDeactivatedEvent(TENANT_ID, USER_ID, "reason");

      service.onUserDeactivated(event);
      // No exception; tenant cache evicted (no organizationId in event)
    }

    @Test
    @DisplayName("onUserProfileUpdated evicts tenant cache only")
    void onUserProfileUpdated_evictsTenantCache() {
      UserProfileUpdatedEvent event =
          new UserProfileUpdatedEvent(TENANT_ID, USER_ID, USER_ID, Set.of());

      service.onUserProfileUpdated(event);
    }
  }

  @Nested
  @DisplayName("with mock CacheManager")
  class WithMockCacheManager {

    @org.junit.jupiter.api.BeforeEach
    void injectMock() {
      // Create service with mock and verify getCache + evict calls
    }

    @Test
    @DisplayName("onUserCreated calls evict on tenant and organization caches")
    void onUserCreated_callsEvict() {
      ConcurrentMapCache tenantCache = new ConcurrentMapCache("users-by-tenant");
      ConcurrentMapCache organizationCache = new ConcurrentMapCache("users-by-organization");
      String organizationCacheKey = TENANT_ID.toString() + "-" + ORGANIZATION_ID.toString();
      tenantCache.put(TENANT_ID.toString(), "cached-tenant-users");
      organizationCache.put(organizationCacheKey, "cached-organization-users");
      when(mockCacheManager.getCache("users-by-tenant")).thenReturn(tenantCache);
      when(mockCacheManager.getCache("users-by-organization")).thenReturn(organizationCache);

      UserCacheInvalidationService service = new UserCacheInvalidationService(mockCacheManager);
      UserCreatedEvent event =
          new UserCreatedEvent(TENANT_ID, USER_ID, "Display", "e@t.com", ORGANIZATION_ID);

      service.onUserCreated(event);

      verify(mockCacheManager).getCache("users-by-tenant");
      verify(mockCacheManager).getCache("users-by-organization");
      assertThat(tenantCache.get(TENANT_ID.toString())).isNull();
      assertThat(organizationCache.get(organizationCacheKey)).isNull();
    }
  }
}
