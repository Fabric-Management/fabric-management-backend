package com.fabricmanagement.production.masterdata.fiber.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.production.execution.batch.domain.BatchStatus;
import com.fabricmanagement.production.execution.batch.infra.repository.BatchRepository;
import com.fabricmanagement.production.masterdata.fiber.domain.Fiber;
import com.fabricmanagement.production.masterdata.fiber.domain.exception.FiberDomainException;
import com.fabricmanagement.production.masterdata.fiber.domain.exception.RecipeInUseException;
import com.fabricmanagement.production.masterdata.fiber.dto.FiberDto;
import com.fabricmanagement.production.masterdata.fiber.dto.UpdateFiberRequest;
import com.fabricmanagement.production.masterdata.fiber.infra.repository.FiberCategoryRepository;
import com.fabricmanagement.production.masterdata.fiber.infra.repository.FiberIsoCodeRepository;
import com.fabricmanagement.production.masterdata.fiber.infra.repository.FiberRepository;
import com.fabricmanagement.production.masterdata.product.domain.Product;
import com.fabricmanagement.production.masterdata.product.infra.repository.ProductRepository;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("FiberService")
class FiberServiceTest {

  private static final UUID TENANT_ID = UUID.randomUUID();
  private static final UUID FIBER_ID = UUID.randomUUID();
  private static final String FIBER_NAME = "COT60_LIN40";

  @Mock private FiberRepository fiberRepository;
  @Mock private ProductRepository productRepository;
  @Mock private FiberCategoryRepository fiberCategoryRepository;
  @Mock private FiberIsoCodeRepository fiberIsoCodeRepository;
  @Mock private DomainEventPublisher eventPublisher;
  @Mock private FiberValidationService validationService;
  @Mock private BatchRepository batchRepository;

  @InjectMocks private FiberService fiberService;

  @BeforeEach
  void setUp() {
    TenantContext.setCurrentTenantId(TENANT_ID);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  // =========================================================================
  // updateFiber — composition (recipe) change
  // =========================================================================

  @Nested
  @DisplayName("updateFiber — composition change")
  class UpdateFiberComposition {

    private Fiber fiber;
    private UpdateFiberRequest requestWithComposition;

    @BeforeEach
    void setUp() {
      fiber = mock(Fiber.class);
      Product product = mock(Product.class);
      lenient().when(product.getId()).thenReturn(FIBER_ID);
      when(fiber.getFiberName()).thenReturn(FIBER_NAME);
      lenient().when(fiber.getVersion()).thenReturn(1L);
      lenient().when(fiber.getProduct()).thenReturn(product);
      when(fiberRepository.findByTenantIdAndId(TENANT_ID, FIBER_ID)).thenReturn(Optional.of(fiber));

      Map<UUID, BigDecimal> newComposition = Map.of(UUID.randomUUID(), new BigDecimal("60.00"));
      requestWithComposition =
          UpdateFiberRequest.builder()
              .fiberName(FIBER_NAME)
              .composition(newComposition)
              .version(1L)
              .build();
    }

    @Test
    @DisplayName("succeeds and persists new composition when no batches are in active production")
    void whenNoActiveBatches_compositionUpdatesSuccessfully() {
      when(batchRepository.existsByTenantIdAndProductIdAndStatusIn(
              TENANT_ID, FIBER_ID, BatchStatus.PRODUCTION_ACTIVE))
          .thenReturn(false);
      when(fiberRepository.save(fiber)).thenReturn(fiber);

      FiberDto result = fiberService.updateFiber(FIBER_ID, requestWithComposition);

      assertThat(result).isNotNull();
      verify(fiber).setComposition(requestWithComposition.getComposition());
      verify(fiberRepository).save(fiber);
    }

    @Test
    @DisplayName("throws RecipeInUseException when batches are RESERVED or IN_PROGRESS")
    void whenActiveBatchesExist_throwsRecipeInUseException() {
      when(batchRepository.existsByTenantIdAndProductIdAndStatusIn(
              TENANT_ID, FIBER_ID, BatchStatus.PRODUCTION_ACTIVE))
          .thenReturn(true);

      assertThatThrownBy(() -> fiberService.updateFiber(FIBER_ID, requestWithComposition))
          .isInstanceOf(RecipeInUseException.class)
          .hasMessageContaining(FIBER_NAME)
          .hasMessageContaining("RESERVED or IN_PROGRESS");

      // Composition must not change and DB must not be touched
      verify(fiber, never()).setComposition(any());
      verify(fiberRepository, never()).save(any());
    }

    @Test
    @DisplayName("thrown RecipeInUseException carries fiberId and fiberName for API details")
    void whenActiveBatchesExist_exceptionCarriesStructuredContext() {
      when(batchRepository.existsByTenantIdAndProductIdAndStatusIn(
              TENANT_ID, FIBER_ID, BatchStatus.PRODUCTION_ACTIVE))
          .thenReturn(true);

      assertThatThrownBy(() -> fiberService.updateFiber(FIBER_ID, requestWithComposition))
          .isInstanceOf(RecipeInUseException.class)
          .satisfies(
              ex -> {
                RecipeInUseException typed = (RecipeInUseException) ex;
                assertThat(typed.getFiberId()).isEqualTo(FIBER_ID);
                assertThat(typed.getFiberName()).isEqualTo(FIBER_NAME);
              });
    }

    @Test
    @DisplayName("skips production check entirely when composition is absent from the request")
    void whenCompositionIsNull_productionCheckIsNotPerformed() {
      UpdateFiberRequest requestWithoutComposition =
          UpdateFiberRequest.builder().fiberName("Updated Name").version(1L).build();
      when(fiberRepository.save(fiber)).thenReturn(fiber);

      fiberService.updateFiber(FIBER_ID, requestWithoutComposition);

      // Guard query must not run — no composition means no recipe risk
      verify(batchRepository, never()).existsByTenantIdAndProductIdAndStatusIn(any(), any(), any());
      verify(fiberRepository).save(fiber);
    }
  }

  // =========================================================================
  // deactivateFiber
  // =========================================================================

  @Nested
  @DisplayName("deactivateFiber")
  class DeactivateFiber {

    private Fiber fiber;

    @BeforeEach
    void setUp() {
      fiber = mock(Fiber.class);
      Product product = mock(Product.class);
      when(product.getId()).thenReturn(FIBER_ID);
      lenient().when(fiber.getFiberName()).thenReturn(FIBER_NAME);
      lenient().when(fiber.getProduct()).thenReturn(product);
      when(fiberRepository.findByTenantIdAndId(TENANT_ID, FIBER_ID)).thenReturn(Optional.of(fiber));
    }

    @Test
    @DisplayName("succeeds and marks fiber deleted when no batches are in active production")
    void whenNoActiveBatches_deactivationSucceeds() {
      when(batchRepository.existsByTenantIdAndProductIdAndStatusIn(
              TENANT_ID, FIBER_ID, BatchStatus.PRODUCTION_ACTIVE))
          .thenReturn(false);
      when(fiberRepository.save(fiber)).thenReturn(fiber);

      fiberService.deactivateFiber(FIBER_ID);

      verify(fiber).delete();
      verify(fiberRepository).save(fiber);
    }

    @Test
    @DisplayName("throws FiberDomainException when batches are RESERVED or IN_PROGRESS")
    void whenActiveBatchesExist_throwsFiberDomainException() {
      when(batchRepository.existsByTenantIdAndProductIdAndStatusIn(
              TENANT_ID, FIBER_ID, BatchStatus.PRODUCTION_ACTIVE))
          .thenReturn(true);

      assertThatThrownBy(() -> fiberService.deactivateFiber(FIBER_ID))
          .isInstanceOf(FiberDomainException.class)
          .hasMessageContaining(FIBER_NAME)
          .hasMessageContaining("RESERVED or IN_PROGRESS");

      // Fiber must not be touched or saved
      verify(fiber, never()).delete();
      verify(fiberRepository, never()).save(any());
    }
  }
}
