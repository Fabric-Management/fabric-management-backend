package com.fabricmanagement.production.core.batch.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.product.core.domain.ProductType;
import com.fabricmanagement.product.fiber.app.FiberQualityQueryService;
import com.fabricmanagement.product.fiber.domain.Fiber;
import com.fabricmanagement.production.core.batch.domain.Batch;
import com.fabricmanagement.production.core.batch.dto.BatchDto;
import com.fabricmanagement.production.core.batch.infra.repository.BatchCertificationRepository;
import com.fabricmanagement.production.core.batch.infra.repository.BatchRepository;
import com.fabricmanagement.production.core.batch.infra.repository.BatchReservationRepository;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

/**
 * Pins the meaning of {@code batch.product_id}: it is a {@code prod_product.id}.
 *
 * <p>A Fiber is reached through {@code Fiber.productId}, never by treating the batch's product id
 * as a {@code prod_fiber.id}. Before BATCH-FK-1 the code did both, in different methods, and the
 * foreign key enforced a third reading. See docs/production/COLOR-ARCHITECTURE.md §3.1.
 */
@ExtendWith(MockitoExtension.class)
class BatchServiceProductLinkTest {

  @Mock private BatchRepository batchRepository;
  @Mock private BatchReservationRepository reservationRepository;
  @Mock private BatchCertificationRepository batchCertificationRepository;
  @Mock private FiberQualityQueryService fiberQualityQueryService;
  @Mock private ApplicationEventPublisher applicationEventPublisher;
  @Mock private BatchPrimaryMeasureService primaryMeasureService;

  @InjectMocks private BatchService batchService;

  private Batch fiberBatch(UUID productId) {
    Batch batch = mock(Batch.class);
    lenient().when(batch.getProductType()).thenReturn(ProductType.FIBER);
    lenient().when(batch.getProductId()).thenReturn(productId);
    lenient().when(batch.getAttributes()).thenReturn(new HashMap<>());
    return batch;
  }

  @Test
  @DisplayName("Should resolve a fibre batch's composition through Fiber.productId")
  void shouldResolveCompositionThroughFiberProductId() {
    UUID productId = UUID.randomUUID();
    UUID cottonId = UUID.randomUUID();
    Fiber fiber = mock(Fiber.class);
    when(fiber.getComposition()).thenReturn(Map.of(cottonId, new BigDecimal("100.00")));
    when(fiberQualityQueryService.findByProductId(productId)).thenReturn(Optional.of(fiber));

    BatchDto dto = batchService.toBatchDto(fiberBatch(productId));

    assertEquals(Map.of(cottonId, new BigDecimal("100.00")), dto.getComposition());
  }

  @Test
  @DisplayName("Should never fall back to treating a product id as a fibre id")
  void shouldNeverFallBackToFiberId() {
    UUID productId = UUID.randomUUID();
    when(fiberQualityQueryService.findByProductId(productId)).thenReturn(Optional.empty());

    batchService.toBatchDto(fiberBatch(productId));

    verify(fiberQualityQueryService).findByProductId(productId);
    verify(fiberQualityQueryService, never()).findByProductIdOrId(any());
  }

  @Test
  @DisplayName("Should return an empty composition when no fibre matches the product")
  void shouldReturnEmptyCompositionWhenNoFiberMatches() {
    UUID productId = UUID.randomUUID();
    when(fiberQualityQueryService.findByProductId(productId)).thenReturn(Optional.empty());

    BatchDto dto = batchService.toBatchDto(fiberBatch(productId));

    assertTrue(dto.getComposition().isEmpty());
  }

  @Test
  @DisplayName("Should not consult the fibre catalogue for a non-fibre batch")
  void shouldNotConsultFiberCatalogueForNonFiberBatch() {
    Batch fabricBatch = mock(Batch.class);
    lenient().when(fabricBatch.getProductType()).thenReturn(ProductType.FABRIC);
    lenient().when(fabricBatch.getAttributes()).thenReturn(new HashMap<>());

    BatchDto dto = batchService.toBatchDto(fabricBatch);

    assertTrue(dto.getComposition().isEmpty());
    verify(fiberQualityQueryService, never()).findByProductId(any());
    verify(fiberQualityQueryService, never()).findByProductIdOrId(any());
  }
}
