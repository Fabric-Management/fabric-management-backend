package com.fabricmanagement.production.spinning.app.adapter;

import com.fabricmanagement.product.core.domain.ProductType;
import com.fabricmanagement.product.yarn.app.port.LegacyDesignationDiscovery;
import com.fabricmanagement.product.yarn.app.port.LegacyDesignationRecord;
import com.fabricmanagement.product.yarn.app.port.LegacyDesignationSourceKind;
import com.fabricmanagement.product.yarn.app.port.LegacyYarnDesignationSource;
import com.fabricmanagement.production.core.batch.domain.Batch;
import com.fabricmanagement.production.core.batch.domain.attributes.YarnAttributes;
import com.fabricmanagement.production.core.batch.infra.repository.BatchRepository;
import com.fabricmanagement.production.core.workorder.domain.WorkOrder;
import com.fabricmanagement.production.core.workorder.infra.repository.WorkOrderRepository;
import com.fabricmanagement.production.spinning.domain.specs.SpinningProductionSpecs;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Bulk production-side discovery for actual batch and target work-order wording. */
@Component
@RequiredArgsConstructor
public class ProductionLegacyYarnDesignationAdapter implements LegacyYarnDesignationSource {

  private final BatchRepository batchRepository;
  private final WorkOrderRepository workOrderRepository;

  @Override
  public LegacyDesignationDiscovery discover(UUID tenantId) {
    List<LegacyDesignationRecord> records = new ArrayList<>();
    EnumMap<LegacyDesignationSourceKind, Long> unlinked =
        new EnumMap<>(LegacyDesignationSourceKind.class);

    for (Batch batch :
        batchRepository.findByTenantIdAndProductTypeAndIsActiveTrue(tenantId, ProductType.YARN)) {
      String rawValue = YarnAttributes.from(batch.getAttributes()).yarnCount();
      addOrCountUnlinked(
          records,
          unlinked,
          batch.getProductId(),
          LegacyDesignationSourceKind.BATCH_ACTUAL,
          rawValue,
          batch.getCreatedAt(),
          batch.getId());
    }

    for (WorkOrder workOrder : workOrderRepository.findByTenantIdAndIsActiveTrue(tenantId)) {
      if (!(workOrder.getProductionSpecs() instanceof SpinningProductionSpecs specs)) {
        continue;
      }
      addOrCountUnlinked(
          records,
          unlinked,
          workOrder.getOutputProductId(),
          LegacyDesignationSourceKind.WORK_ORDER_TARGET,
          specs.targetYarnCount(),
          workOrder.getCreatedAt(),
          workOrder.getId());
    }

    return new LegacyDesignationDiscovery(records, unlinked);
  }

  private void addOrCountUnlinked(
      List<LegacyDesignationRecord> records,
      Map<LegacyDesignationSourceKind, Long> unlinked,
      UUID productId,
      LegacyDesignationSourceKind kind,
      String rawValue,
      java.time.Instant recordedAt,
      UUID sourceRecordId) {
    if (productId == null) {
      if (rawValue != null && !rawValue.isBlank()) {
        unlinked.merge(kind, 1L, Long::sum);
      }
      return;
    }
    records.add(
        new LegacyDesignationRecord(
            productId, kind, rawValue, recordedAt, sourceRecordId.toString()));
  }
}
