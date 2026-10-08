package com.fabricmanagement.sales.salesorder.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.platform.realtime.domain.LiveActor;
import com.fabricmanagement.platform.realtime.domain.LiveReadResult;
import com.fabricmanagement.platform.realtime.domain.LiveRevision;
import com.fabricmanagement.platform.user.app.UserQueryService;
import com.fabricmanagement.platform.user.domain.DataScope;
import com.fabricmanagement.sales.common.app.SalesAccessScopeResolver;
import com.fabricmanagement.sales.common.app.SalesAccessScopeResolver.AccessScope;
import com.fabricmanagement.sales.salesorder.infra.repository.SalesOrderRepository;
import com.fabricmanagement.sales.salesorder.infra.repository.SalesOrderRepository.LiveRevisionView;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** A sales order as a live resource (CEDIT-05 §4.3, §6): fresh access, root version only. */
@ExtendWith(MockitoExtension.class)
class SalesOrderLiveRevisionSourceTest {

  private final UUID tenant = UUID.randomUUID();
  private final UUID user = UUID.randomUUID();
  private final UUID creator = UUID.randomUUID();
  private final UUID orderId = UUID.randomUUID();
  private final LiveActor actor = new LiveActor(tenant, user, Instant.now().plusSeconds(600));

  @Mock private SalesOrderRepository orders;
  @Mock private SalesAccessScopeResolver resolver;
  @Mock private UserQueryService users;

  private SalesOrderLiveRevisionSource source;

  @BeforeEach
  void setUp() {
    source = new SalesOrderLiveRevisionSource(orders, new SalesOrderAccessPolicy(resolver), users);
    TenantContext.setCurrentTenantId(tenant);
  }

  @AfterEach
  void clear() {
    TenantContext.clear();
  }

  @Test
  @DisplayName("L01: the committed version as a decimal string; version 0 is a revision")
  void versionIsTheRevision() {
    activeUserWith(new AccessScope(DataScope.ORGANIZATION, Set.of()));
    when(orders.findLiveRevisionViewByTenantIdAndId(tenant, orderId))
        .thenReturn(Optional.of(view(tenant, true, creator, 0L)));
    assertThat(source.read(actor, orderId))
        .isEqualTo(new LiveReadResult.Visible(new LiveRevision("0")));

    when(orders.findLiveRevisionViewByTenantIdAndId(tenant, orderId))
        .thenReturn(Optional.of(view(tenant, true, creator, 12L)));
    assertThat(source.read(actor, orderId))
        .isEqualTo(new LiveReadResult.Visible(new LiveRevision("12")));
  }

  @Test
  @DisplayName("L10: access is resolved FRESH and only the order's root projection is read")
  void freshAccessAndRootProjectionOnly() {
    activeUserWith(new AccessScope(DataScope.GLOBAL, Set.of()));
    when(orders.findLiveRevisionViewByTenantIdAndId(tenant, orderId))
        .thenReturn(Optional.of(view(tenant, true, creator, 3L)));

    source.read(actor, orderId);

    verify(resolver)
        .resolve(tenant, user, "read", SalesAccessScopeResolver.PermissionFreshness.FRESH);
    verify(orders).findLiveRevisionViewByTenantIdAndId(tenant, orderId);
    verifyNoMoreInteractions(orders);
  }

  @Test
  @DisplayName("L10: a deactivated user is forbidden before anything else is read")
  void inactiveUserIsForbidden() {
    when(users.isActive(tenant, user)).thenReturn(false);

    assertThat(source.read(actor, orderId)).isEqualTo(LiveReadResult.Hidden.FORBIDDEN);
    verifyNoInteractions(orders, resolver);
  }

  @Test
  @DisplayName("L10: no sales read permission at all is forbidden")
  void noReadPermissionIsForbidden() {
    activeUserWith(AccessScope.denied());

    assertThat(source.read(actor, orderId)).isEqualTo(LiveReadResult.Hidden.FORBIDDEN);
    verifyNoInteractions(orders);
  }

  @Test
  @DisplayName("L03: missing, deleted, foreign-tenant or out-of-scope orders are not found")
  void hiddenOrdersAreNotFound() {
    activeUserWith(new AccessScope(DataScope.OWN, Set.of(user)));

    when(orders.findLiveRevisionViewByTenantIdAndId(tenant, orderId)).thenReturn(Optional.empty());
    assertThat(source.read(actor, orderId)).isEqualTo(LiveReadResult.Hidden.NOT_FOUND);

    when(orders.findLiveRevisionViewByTenantIdAndId(tenant, orderId))
        .thenReturn(Optional.of(view(tenant, false, user, 1L)));
    assertThat(source.read(actor, orderId)).isEqualTo(LiveReadResult.Hidden.NOT_FOUND);

    when(orders.findLiveRevisionViewByTenantIdAndId(tenant, orderId))
        .thenReturn(Optional.of(view(UUID.randomUUID(), true, user, 1L)));
    assertThat(source.read(actor, orderId)).isEqualTo(LiveReadResult.Hidden.NOT_FOUND);

    when(orders.findLiveRevisionViewByTenantIdAndId(tenant, orderId))
        .thenReturn(Optional.of(view(tenant, true, creator, 1L)));
    assertThat(source.read(actor, orderId)).isEqualTo(LiveReadResult.Hidden.NOT_FOUND);

    when(orders.findLiveRevisionViewByTenantIdAndId(tenant, orderId))
        .thenReturn(Optional.of(view(tenant, true, user, 1L)));
    assertThat(source.read(actor, orderId))
        .isEqualTo(new LiveReadResult.Visible(new LiveRevision("1")));
  }

  @Test
  @DisplayName("L03: DEPARTMENT reach follows the resolver's current department members")
  void departmentReachUsesTheResolver() {
    activeUserWith(new AccessScope(DataScope.DEPARTMENT, Set.of(user, creator)));
    when(orders.findLiveRevisionViewByTenantIdAndId(tenant, orderId))
        .thenReturn(Optional.of(view(tenant, true, creator, 5L)));

    assertThat(source.read(actor, orderId))
        .isEqualTo(new LiveReadResult.Visible(new LiveRevision("5")));

    when(resolver.resolve(any(), any(), any(), any()))
        .thenReturn(new AccessScope(DataScope.DEPARTMENT, Set.of(user)));
    assertThat(source.read(actor, orderId)).isEqualTo(LiveReadResult.Hidden.NOT_FOUND);
  }

  @Test
  @DisplayName("L13: a read outside the actor's own tenant scope is refused, never guessed")
  void requiresTheActorsTenantScope() {
    TenantContext.setCurrentTenantId(UUID.randomUUID());

    assertThatThrownBy(() -> source.read(actor, orderId)).isInstanceOf(IllegalStateException.class);
    verifyNoInteractions(orders, resolver, users);
  }

  private void activeUserWith(AccessScope scope) {
    when(users.isActive(tenant, user)).thenReturn(true);
    when(resolver.resolve(any(), any(), any(), any())).thenReturn(scope);
  }

  private static LiveRevisionView view(
      UUID tenantId, boolean active, UUID createdBy, long version) {
    UUID id = UUID.randomUUID();
    return new LiveRevisionView() {
      @Override
      public UUID getId() {
        return id;
      }

      @Override
      public UUID getTenantId() {
        return tenantId;
      }

      @Override
      public Boolean getIsActive() {
        return active;
      }

      @Override
      public UUID getCreatedBy() {
        return createdBy;
      }

      @Override
      public Long getVersion() {
        return version;
      }
    };
  }
}
