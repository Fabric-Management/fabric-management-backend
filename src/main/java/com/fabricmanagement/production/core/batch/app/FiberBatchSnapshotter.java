package com.fabricmanagement.production.core.batch.app;

import com.fabricmanagement.product.core.domain.ProductType;
import com.fabricmanagement.product.fiber.app.FiberQualityQueryService;
import com.fabricmanagement.production.core.batch.domain.BatchCompositionSnapshot;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Composition snapshots for FIBER batches created by system flows (FIBER-CATALOG-1). A goods
 * receipt records the received product's fibre definition; a physical blend records what was
 * actually mixed, computed from its inputs. A batch whose composition cannot be established gets no
 * snapshot: its composition is unknown, not pure.
 */
@Component
@RequiredArgsConstructor
public class FiberBatchSnapshotter {

  private final FiberQualityQueryService fiberQualityQueryService;

  public Map<String, Object> withDefinitionSnapshot(
      UUID tenantId, UUID productId, ProductType productType, Map<String, Object> attributes) {
    Map<String, Object> result = attributes != null ? new HashMap<>(attributes) : new HashMap<>();
    if (productType != ProductType.FIBER || BatchCompositionSnapshot.isRecorded(result)) {
      return result;
    }
    fiberQualityQueryService
        .definitionComposition(tenantId, productId)
        .ifPresent(
            effective ->
                result.put(
                    BatchCompositionSnapshot.ATTRIBUTE_KEY,
                    BatchCompositionSnapshot.toAttribute(effective.composition())));
    return result;
  }

  /**
   * Snapshot for a physically blended FIBER output, computed from the inputs' recorded snapshots
   * weighted by their consumption shares. A non-FIBER input or an input without a readable snapshot
   * leaves the output unknown; the output product's catalogue definition is never used, because it
   * describes what the product should be, not what was mixed.
   */
  public Map<String, Object> withBlendSnapshot(
      ProductType productType,
      List<BatchCompositionSnapshot.InputShare> inputs,
      Map<String, Object> attributes) {
    Map<String, Object> result = attributes != null ? new HashMap<>(attributes) : new HashMap<>();
    if (productType != ProductType.FIBER || BatchCompositionSnapshot.isRecorded(result)) {
      return result;
    }
    BatchCompositionSnapshot.mix(inputs)
        .ifPresent(
            mixed ->
                result.put(
                    BatchCompositionSnapshot.ATTRIBUTE_KEY,
                    BatchCompositionSnapshot.toAttribute(mixed)));
    return result;
  }
}
