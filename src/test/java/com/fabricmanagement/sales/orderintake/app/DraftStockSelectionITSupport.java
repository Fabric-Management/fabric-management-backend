package com.fabricmanagement.sales.orderintake.app;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.SystemTransactionExecutor;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.platform.user.domain.DataScope;
import com.fabricmanagement.product.core.api.query.ProductSalesDefinitionQueryService;
import com.fabricmanagement.product.core.domain.ProductType;
import com.fabricmanagement.product.core.dto.ProductSalesDefinitionDto;
import com.fabricmanagement.sales.common.app.SalesAccessScopeResolver;
import com.fabricmanagement.sales.orderintake.dto.*;
import com.fabricmanagement.testsupport.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Real PostgreSQL ownership and transactions; only user permissions/product catalogue are
 * controlled.
 */
abstract class DraftStockSelectionITSupport extends AbstractIntegrationTest {
  @Autowired SystemTransactionExecutor system;
  @Autowired TransactionTemplate transactions;
  @Autowired DraftStockSelectionService service;
  @Autowired OrderIntakeStockPreviewService previews;
  @MockitoBean SalesAccessScopeResolver permissions;
  @MockitoBean ProductSalesDefinitionQueryService products;
  @MockitoBean com.fabricmanagement.platform.user.app.UserQueryService users;
  UUID tenant, otherTenant, product, batch, actor, otherActor;
  final List<UUID> rolls = new ArrayList<>();

  @BeforeEach
  void seedDraftRolls() {
    tenant = UUID.randomUUID();
    otherTenant = UUID.randomUUID();
    product = UUID.randomUUID();
    batch = UUID.randomUUID();
    actor = UUID.randomUUID();
    otherActor = UUID.randomUUID();
    rolls.clear();
    when(users.isActive(any(), any())).thenReturn(true);
    when(permissions.resolve(any(), any(), any(), any()))
        .thenReturn(new SalesAccessScopeResolver.AccessScope(DataScope.GLOBAL, Set.of()));
    when(products.find(eq(tenant), eq(product)))
        .thenReturn(
            Optional.of(
                new ProductSalesDefinitionDto(
                    product,
                    "FABRIC-ROLL-TEST",
                    "Cotton poplin",
                    ProductType.FABRIC,
                    "M",
                    true,
                    List.of(),
                    List.of())));
    system.executeInTransaction(
        jdbc -> {
          for (UUID id : List.of(tenant, otherTenant))
            jdbc.update(
                "insert into common_tenant.common_tenant(id,uid,slug,name,status) values (?,?,?,'Roll selection test','ACTIVE')",
                id,
                id.toString(),
                id.toString());
          jdbc.update(
              "insert into production.prod_product(id,tenant_id,uid,product_type,unit) values (?,?,?,'FABRIC','M')",
              product,
              tenant,
              product.toString());
          jdbc.update(
              """
          insert into production.production_execution_batch
          (id,tenant_id,uid,product_id,product_type,batch_code,quantity,reserved_quantity,consumed_quantity,waste_quantity,unit,status)
          values (?,?,?,?,'FABRIC',?,1200,0,0,0,'M','AVAILABLE')
          """,
              batch,
              tenant,
              batch.toString(),
              product,
              batch.toString());
          for (int index = 0; index < 12; index++) {
            UUID roll = UUID.randomUUID();
            rolls.add(roll);
            jdbc.update(
                """
            insert into production.stock_unit
            (id,uid,created_at,updated_at,tenant_id,barcode,batch_id,package_type,product_type,initial_weight,current_weight,
             unit,status,source_type,source_id,quality_disposition,length,length_unit,initial_length,initial_length_unit)
            values (?,?,now(),now(),?,?,?,'ROLL','FABRIC',30,30,'KG','AVAILABLE','PRODUCTION',?,'RELEASED',100,'M',100,'M')
            """,
                roll,
                roll.toString(),
                tenant,
                roll.toString(),
                batch,
                UUID.randomUUID());
          }
          return null;
        });
  }

  OrderIntakeStockPreviewRequest request() {
    return new OrderIntakeStockPreviewRequest(
        product,
        null,
        null,
        null,
        null,
        new BigDecimal("700"),
        "M",
        false,
        new BigDecimal("5"),
        new BigDecimal("5"),
        null);
  }

  DraftStockSelectionDto open(UUID user) {
    return in(
        tenant,
        () ->
            service.open(
                new DraftStockSelectionRequests.Open(UUID.randomUUID(), UUID.randomUUID(), product),
                user));
  }

  DraftStockSelectionRequests.Acquire command(DraftStockSelectionDto session, UUID operation) {
    var preview = in(tenant, () -> previews.preview(request(), actor));
    var option = preview.evaluation().options().get(preview.evaluation().mainOptionIndex());
    return new DraftStockSelectionRequests.Acquire(
        operation,
        session.selectionRevision(),
        request(),
        option.optionKey(),
        session.grants().stream().map(DraftStockSelectionDto.Grant::token).toList());
  }

  DraftStockSelectionDto acquire(DraftStockSelectionDto session, UUID user) {
    var command = command(session, UUID.randomUUID());
    return in(tenant, () -> service.acquire(session.selectionId(), command, user));
  }

  <T> T in(UUID tenantId, Supplier<T> action) {
    return TenantContext.executeInTenantContext(
        tenantId,
        () ->
            transactions.execute(
                status -> {
                  TenantContext.setCurrentUserId(actor);
                  return action.get();
                }));
  }
}
