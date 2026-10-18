package com.fabricmanagement.production.core.stockunit.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fabricmanagement.common.infrastructure.persistence.SystemTransactionExecutor;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.production.core.stockunit.infra.repository.StockUnitRepository;
import com.fabricmanagement.testsupport.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

class StockUnitInitialLengthIT extends AbstractIntegrationTest {
  @Autowired SystemTransactionExecutor system;
  @Autowired StockUnitRepository units;
  final UUID tenant = UUID.randomUUID(),
      otherTenant = UUID.randomUUID(),
      batch = UUID.randomUUID(),
      unit = UUID.randomUUID();

  @BeforeEach
  void seedMeasuredPhysicalRoll() {
    system.executeInTransaction(
        jdbc -> {
          for (UUID id : new UUID[] {tenant, otherTenant}) {
            jdbc.update(
                "INSERT INTO common_tenant.common_tenant (id,uid,slug,name,status) VALUES (?,?,?,'Roll test','ACTIVE')",
                id,
                id.toString(),
                id.toString());
          }
          UUID product = UUID.randomUUID();
          jdbc.update(
              "INSERT INTO production.prod_product (id,tenant_id,uid,product_type,unit) VALUES (?,?,?,'FABRIC','M')",
              product,
              tenant,
              product.toString());
          jdbc.update(
              """
          INSERT INTO production.production_execution_batch
            (id,tenant_id,uid,product_id,product_type,batch_code,quantity,reserved_quantity,consumed_quantity,waste_quantity,unit,status)
          VALUES (?,?,?,?,'FABRIC',?,100,0,0,0,'M','AVAILABLE')
          """,
              batch,
              tenant,
              batch.toString(),
              product,
              batch.toString());
          jdbc.update(
              """
          INSERT INTO production.stock_unit
            (id,uid,created_at,updated_at,tenant_id,barcode,batch_id,package_type,product_type,
             initial_weight,current_weight,unit,status,source_type,source_id,quality_disposition,
             length,length_unit,initial_length,initial_length_unit)
          VALUES (?,?,now(),now(),?,?,?,'ROLL','FABRIC',30,30,'KG','AVAILABLE','PRODUCTION',?,'RELEASED',100,'M',100,'M')
          """,
              unit,
              unit.toString(),
              tenant,
              unit.toString(),
              batch,
              UUID.randomUUID());
          return null;
        });
  }

  @Test
  void currentLengthUpdateRoundTripsWithoutChangingInitialAndOtherTenantCannotRead() {
    TenantContext.executeInTenantContext(
        tenant,
        () -> {
          StockUnit read = units.findByIdAndTenantIdAndIsActiveTrue(unit, tenant).orElseThrow();
          read.recordLength(new BigDecimal("70"), "M");
          units.saveAndFlush(read);
          return null;
        });
    StockUnit reread =
        TenantContext.executeInTenantContext(
            tenant, () -> units.findByIdAndTenantIdAndIsActiveTrue(unit, tenant).orElseThrow());
    assertThat(reread.getLength()).isEqualByComparingTo("70");
    assertThat(reread.getInitialLength()).isEqualByComparingTo("100");
    assertThat(reread.getInitialLengthUnit()).isEqualTo("M");
    assertThat(
            TenantContext.executeInTenantContext(
                otherTenant, () -> units.findByIdAndTenantIdAndIsActiveTrue(unit, otherTenant)))
        .isEmpty();
  }

  @Test
  void databaseRejectsUnpairedNonPositiveAndInvalidUnitEvidence() {
    for (String assignment :
        new String[] {
          "initial_length = 0", "initial_length_unit = NULL", "initial_length_unit = 'KG'"
        }) {
      assertThatThrownBy(
              () ->
                  system.executeInTransaction(
                      jdbc ->
                          jdbc.update(
                              "UPDATE production.stock_unit SET " + assignment + " WHERE id = ?",
                              unit)))
          .isInstanceOf(DataIntegrityViolationException.class);
    }
    system.executeInTransaction(
        jdbc ->
            jdbc.update(
                "UPDATE production.stock_unit SET initial_length = NULL, initial_length_unit = NULL WHERE id = ?",
                unit));
    StockUnit legacy =
        TenantContext.executeInTenantContext(
            tenant, () -> units.findByIdAndTenantIdAndIsActiveTrue(unit, tenant).orElseThrow());
    assertThat(legacy.getInitialLength()).isNull();
    assertThat(legacy.getLength()).isEqualByComparingTo("100");
  }
}
