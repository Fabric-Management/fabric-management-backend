package com.fabricmanagement.production.core.batch.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.product.core.domain.ProductType;
import com.fabricmanagement.product.fiber.app.FiberQualityQueryService;
import com.fabricmanagement.product.fiber.app.FiberQualityQueryService.EffectiveComposition;
import com.fabricmanagement.product.fiber.app.FiberQualityQueryService.ProfileSource;
import com.fabricmanagement.product.fiber.app.FiberQualityQueryService.QualityResolution;
import com.fabricmanagement.product.fiber.domain.FiberKind;
import com.fabricmanagement.product.fiber.domain.FiberQualityStandard;
import com.fabricmanagement.production.core.batch.domain.Batch;
import com.fabricmanagement.production.core.batch.domain.BatchCompositionSnapshot;
import com.fabricmanagement.production.core.batch.domain.exception.BatchDomainException;
import com.fabricmanagement.production.core.batch.dto.BatchDto;
import com.fabricmanagement.production.core.batch.dto.CreateBatchRequest;
import com.fabricmanagement.production.core.batch.infra.repository.BatchCertificationRepository;
import com.fabricmanagement.production.core.batch.infra.repository.BatchRepository;
import com.fabricmanagement.production.core.batch.infra.repository.BatchReservationRepository;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

/**
 * Pins the meaning of {@code batch.product_id} (a {@code prod_product.id}, reached through {@code
 * Fiber.productId}, never read as a {@code prod_fiber.id}) and the FIBER-CATALOG-1 composition
 * snapshot: written once at creation, shown from the snapshot afterwards, and never replaced by the
 * current catalogue definition or presented as pure when malformed.
 */
@ExtendWith(MockitoExtension.class)
class BatchServiceProductLinkTest {

  private static final UUID TENANT_ID = UUID.randomUUID();

  @Mock private BatchRepository batchRepository;
  @Mock private BatchReservationRepository reservationRepository;
  @Mock private BatchCertificationRepository batchCertificationRepository;
  @Mock private FiberQualityQueryService fiberQualityQueryService;
  @Mock private ApplicationEventPublisher applicationEventPublisher;
  @Mock private BatchPrimaryMeasureService primaryMeasureService;

  @InjectMocks private BatchService batchService;

  @BeforeEach
  void setUp() {
    TenantContext.setCurrentTenantId(TENANT_ID);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  private Batch fiberBatch(UUID productId, Map<String, Object> attributes) {
    Batch batch = mock(Batch.class);
    lenient().when(batch.getProductType()).thenReturn(ProductType.FIBER);
    lenient().when(batch.getProductId()).thenReturn(productId);
    lenient().when(batch.getTenantId()).thenReturn(TENANT_ID);
    lenient().when(batch.getAttributes()).thenReturn(attributes);
    return batch;
  }

  @Test
  @DisplayName("shows the stored snapshot and never consults the current fibre definition")
  void showsStoredSnapshot() {
    UUID productId = UUID.randomUUID();
    UUID cotton = UUID.randomUUID();
    UUID polyester = UUID.randomUUID();
    Map<UUID, BigDecimal> snapshot =
        Map.of(cotton, new BigDecimal("60"), polyester, new BigDecimal("40"));
    Map<String, Object> attributes = new HashMap<>();
    attributes.put(
        BatchCompositionSnapshot.ATTRIBUTE_KEY, BatchCompositionSnapshot.toAttribute(snapshot));

    BatchDto dto = batchService.toBatchDto(fiberBatch(productId, attributes));

    assertThat(dto.getComposition()).isEqualTo(snapshot);
    verify(fiberQualityQueryService, never()).definitionComposition(any(), any());
  }

  @Test
  @DisplayName("a legacy batch without snapshot shows its fibre definition via Fiber.productId")
  void legacyBatchUsesDefinitionThroughProductId() {
    UUID productId = UUID.randomUUID();
    UUID fiberId = UUID.randomUUID();
    Map<UUID, BigDecimal> pure = Map.of(fiberId, new BigDecimal("100"));
    when(fiberQualityQueryService.definitionComposition(TENANT_ID, productId))
        .thenReturn(
            Optional.of(
                new EffectiveComposition(fiberId, UUID.randomUUID(), FiberKind.PURE, "CO", pure)));

    BatchDto dto = batchService.toBatchDto(fiberBatch(productId, new HashMap<>()));

    assertThat(dto.getComposition()).isEqualTo(pure);
    verify(fiberQualityQueryService, never()).findByProductId(any(), eq(fiberId));
  }

  @Test
  @DisplayName("no fibre definition means an empty (unknown) composition")
  void emptyWhenNoFibreMatches() {
    UUID productId = UUID.randomUUID();
    when(fiberQualityQueryService.definitionComposition(TENANT_ID, productId))
        .thenReturn(Optional.empty());

    assertThat(batchService.toBatchDto(fiberBatch(productId, new HashMap<>())).getComposition())
        .isEmpty();
  }

  @Test
  @DisplayName("a malformed snapshot is shown as unknown, never replaced by the definition")
  void malformedSnapshotIsUnknown() {
    Map<String, Object> attributes = new HashMap<>();
    attributes.put(BatchCompositionSnapshot.ATTRIBUTE_KEY, Map.of("not-a-uuid", "60"));

    BatchDto dto = batchService.toBatchDto(fiberBatch(UUID.randomUUID(), attributes));

    assertThat(dto.getComposition()).isEmpty();
    verify(fiberQualityQueryService, never()).definitionComposition(any(), any());
  }

  @Test
  @DisplayName("does not consult the fibre catalogue for a non-fibre batch")
  void nonFibreBatch() {
    Batch fabricBatch = mock(Batch.class);
    lenient().when(fabricBatch.getProductType()).thenReturn(ProductType.FABRIC);
    lenient().when(fabricBatch.getAttributes()).thenReturn(new HashMap<>());

    assertThat(batchService.toBatchDto(fabricBatch).getComposition()).isEmpty();
    verify(fiberQualityQueryService, never()).definitionComposition(any(), any());
    verify(fiberQualityQueryService, never()).findByProductId(any(), any());
  }

  @Test
  @DisplayName("FIBER create stores the effective snapshot and the resolved profile")
  void fiberCreateStoresSnapshotAndResolvedProfile() {
    UUID productId = UUID.randomUUID();
    UUID cotton = UUID.randomUUID();
    UUID polyester = UUID.randomUUID();
    UUID blendId = UUID.randomUUID();
    Map<UUID, BigDecimal> override =
        Map.of(cotton, new BigDecimal("62.5"), polyester, new BigDecimal("37.5"));
    EffectiveComposition effective =
        new EffectiveComposition(blendId, null, FiberKind.BLEND, "Blend", override);
    FiberQualityStandard profile = mock(FiberQualityStandard.class);
    UUID profileId = UUID.randomUUID();
    when(profile.getId()).thenReturn(profileId);
    when(fiberQualityQueryService.resolveEffectiveComposition(TENANT_ID, productId, override))
        .thenReturn(effective);
    when(fiberQualityQueryService.resolve(TENANT_ID, effective, null))
        .thenReturn(new QualityResolution(profile, ProfileSource.EXACT_FIBER_DEFAULT));
    when(batchRepository.save(any(Batch.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    batchService.create(
        CreateBatchRequest.builder()
            .productId(productId)
            .productType(ProductType.FIBER)
            .batchCode("FIB-625")
            .quantity(BigDecimal.TEN)
            .unit("KG")
            .composition(override)
            .build());

    ArgumentCaptor<Batch> saved = ArgumentCaptor.forClass(Batch.class);
    verify(batchRepository).save(saved.capture());
    assertThat(saved.getValue().getQualityStandardId()).isEqualTo(profileId);
    assertThat(BatchCompositionSnapshot.read(saved.getValue().getAttributes())).contains(override);
  }

  @Test
  @DisplayName("FIBER create of a product without fibre definition stores no snapshot/profile")
  void fiberCreateWithoutDefinitionStaysUnknown() {
    UUID productId = UUID.randomUUID();
    when(fiberQualityQueryService.findByProductId(TENANT_ID, productId))
        .thenReturn(Optional.empty());
    when(batchRepository.save(any(Batch.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    batchService.create(
        CreateBatchRequest.builder()
            .productId(productId)
            .productType(ProductType.FIBER)
            .batchCode("FIB-UNKNOWN")
            .quantity(BigDecimal.TEN)
            .unit("KG")
            .build());

    ArgumentCaptor<Batch> saved = ArgumentCaptor.forClass(Batch.class);
    verify(batchRepository).save(saved.capture());
    assertThat(saved.getValue().getQualityStandardId()).isNull();
    assertThat(BatchCompositionSnapshot.isRecorded(saved.getValue().getAttributes())).isFalse();
    verify(fiberQualityQueryService, never()).resolve(any(), any(), any());
  }

  @Test
  @DisplayName("non-FIBER create rejects a composition override or a fibre quality profile")
  void nonFiberCreateRejectsFibreInputs() {
    assertThatThrownBy(
            () ->
                batchService.create(
                    CreateBatchRequest.builder()
                        .productId(UUID.randomUUID())
                        .productType(ProductType.YARN)
                        .batchCode("YRN-1")
                        .quantity(BigDecimal.TEN)
                        .unit("KG")
                        .composition(Map.of(UUID.randomUUID(), new BigDecimal("100")))
                        .build()))
        .isInstanceOf(BatchDomainException.class);
    assertThatThrownBy(
            () ->
                batchService.create(
                    CreateBatchRequest.builder()
                        .productId(UUID.randomUUID())
                        .productType(ProductType.YARN)
                        .batchCode("YRN-2")
                        .quantity(BigDecimal.TEN)
                        .unit("KG")
                        .qualityStandardId(UUID.randomUUID())
                        .build()))
        .isInstanceOf(BatchDomainException.class);
    verify(batchRepository, never()).save(any());
  }
}
