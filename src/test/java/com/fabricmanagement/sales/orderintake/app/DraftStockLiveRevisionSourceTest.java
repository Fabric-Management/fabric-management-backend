package com.fabricmanagement.sales.orderintake.app;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.platform.realtime.app.LiveEditLeaseService;
import com.fabricmanagement.platform.realtime.domain.*;
import com.fabricmanagement.platform.user.app.UserQueryService;
import com.fabricmanagement.platform.user.domain.DataScope;
import com.fabricmanagement.product.core.api.query.ProductSalesDefinitionQueryService;
import com.fabricmanagement.product.core.domain.ProductType;
import com.fabricmanagement.product.core.dto.ProductSalesDefinitionDto;
import com.fabricmanagement.production.core.stockunit.api.*;
import com.fabricmanagement.sales.common.app.SalesAccessScopeResolver;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DraftStockLiveRevisionSourceTest {
  @Mock LiveEditLeaseService leases;
  @Mock UserQueryService users;
  @Mock SalesAccessScopeResolver permissions;
  @Mock ProductSalesDefinitionQueryService products;
  @Mock StockUnitOwnershipPort ownership;
  @InjectMocks DraftStockLiveRevisionSource source;
  final UUID tenant = UUID.randomUUID(), user = UUID.randomUUID(), product = UUID.randomUUID();
  final LiveActor actor = new LiveActor(tenant, user, Instant.parse("2026-10-19T10:00:00Z"));

  @BeforeEach
  void bind() {
    TenantContext.setCurrentTenantId(tenant);
  }

  @AfterEach
  void clear() {
    TenantContext.clear();
  }

  @Test
  void reconnectReadsCurrentStockAndLeaseRevisionsWithoutRenewingOwnership() {
    readable();
    when(products.find(tenant, product))
        .thenReturn(
            Optional.of(
                new ProductSalesDefinitionDto(
                    product,
                    "FABRIC-001",
                    "Cotton poplin",
                    ProductType.FABRIC,
                    "M",
                    true,
                    List.of(),
                    List.of())));
    when(ownership.availabilityRevision(product)).thenReturn("stock-1", "stock-2");
    when(leases.leaseRevision(StockUnitLeaseKeys.resource(product), 1))
        .thenReturn(new LiveRevision("lease-1"), new LiveRevision("lease-2"));
    assertThat(source.read(actor, product))
        .isEqualTo(
            new LiveReadResult.Visible(
                new LiveRevision("stock-1"), null, new LiveRevision("lease-1")));
    assertThat(source.read(actor, product))
        .isEqualTo(
            new LiveReadResult.Visible(
                new LiveRevision("stock-2"), null, new LiveRevision("lease-2")));
    verify(leases, times(2)).leaseRevision(StockUnitLeaseKeys.resource(product), 1);
    verifyNoMoreInteractions(leases);
  }

  @Test
  void revocationEndsTheChannelBeforeReadingProductOrOwnership() {
    when(users.isActive(tenant, user)).thenReturn(true);
    when(permissions.resolve(
            tenant, user, "read", SalesAccessScopeResolver.PermissionFreshness.FRESH))
        .thenReturn(SalesAccessScopeResolver.AccessScope.denied());
    assertThat(source.read(actor, product)).isEqualTo(LiveReadResult.Hidden.FORBIDDEN);
    verifyNoInteractions(products, ownership, leases);
  }

  @Test
  void missingOrForeignProductIsHidden() {
    readable();
    when(products.find(tenant, product)).thenReturn(Optional.empty());
    assertThat(source.read(actor, product)).isEqualTo(LiveReadResult.Hidden.NOT_FOUND);
    verifyNoInteractions(ownership, leases);
  }

  @Test
  void anotherTenantContextCannotBeUsedForTheActor() {
    TenantContext.setCurrentTenantId(UUID.randomUUID());
    assertThatThrownBy(() -> source.read(actor, product)).isInstanceOf(IllegalStateException.class);
    verifyNoInteractions(users, products, ownership, leases, permissions);
  }

  private void readable() {
    when(users.isActive(tenant, user)).thenReturn(true);
    when(permissions.resolve(
            tenant, user, "read", SalesAccessScopeResolver.PermissionFreshness.FRESH))
        .thenReturn(new SalesAccessScopeResolver.AccessScope(DataScope.GLOBAL, Set.of()));
  }
}
