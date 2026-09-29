package com.fabricmanagement.product.fiber.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.product.core.infra.repository.ProductRepository;
import com.fabricmanagement.product.fiber.app.port.FiberUsagePort;
import com.fabricmanagement.product.fiber.domain.Fiber;
import com.fabricmanagement.product.fiber.domain.FiberCatalog;
import com.fabricmanagement.product.fiber.domain.FiberStatus;
import com.fabricmanagement.product.fiber.domain.MaterialSource;
import com.fabricmanagement.product.fiber.domain.exception.FiberDomainException;
import com.fabricmanagement.product.fiber.domain.exception.RecipeInUseException;
import com.fabricmanagement.product.fiber.domain.reference.FiberCategory;
import com.fabricmanagement.product.fiber.dto.CreateFiberRequest;
import com.fabricmanagement.product.fiber.dto.FiberDto;
import com.fabricmanagement.product.fiber.dto.UpdateFiberRequest;
import com.fabricmanagement.product.fiber.infra.repository.FiberRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * FIBER-CATALOG-1 service rules that need no database: shared read scope, shared read-only
 * mutations, owner-only pure publication, blend-only tenant creation and recipe-in-use guards.
 * Database-backed behaviour (RLS, unique blend identity, publication) lives in the ITs.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("FiberService")
class FiberServiceTest {

  private static final UUID TENANT_ID = UUID.randomUUID();
  private static final UUID FIBER_ID = UUID.randomUUID();
  private static final UUID PRODUCT_ID = UUID.randomUUID();
  private static final String FIBER_NAME = "CO 60% / PES 40%";
  private static final List<UUID> READ_SCOPE = FiberCatalog.readScope(TENANT_ID);

  @Mock private FiberRepository fiberRepository;
  @Mock private ProductRepository productRepository;
  @Mock private FiberReferenceQueryService referenceQueryService;
  @Mock private DomainEventPublisher eventPublisher;
  @Mock private FiberValidationService validationService;
  @Mock private FiberUsagePort fiberUsagePort;
  @Mock private FiberDtoAssembler dtoAssembler;

  private FiberService fiberService;

  @BeforeEach
  void setUp() {
    TenantContext.setCurrentTenantId(TENANT_ID);
    fiberService =
        new FiberService(
            fiberRepository,
            productRepository,
            referenceQueryService,
            eventPublisher,
            validationService,
            fiberUsagePort,
            dtoAssembler,
            new ObjectMapper());
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  private static Fiber ownBlend() {
    Fiber fiber = mock(Fiber.class);
    lenient().when(fiber.getId()).thenReturn(FIBER_ID);
    lenient().when(fiber.getTenantId()).thenReturn(TENANT_ID);
    lenient().when(fiber.getFiberName()).thenReturn(FIBER_NAME);
    lenient().when(fiber.getProductId()).thenReturn(PRODUCT_ID);
    lenient().when(fiber.getVersion()).thenReturn(1L);
    lenient().when(fiber.getIsActive()).thenReturn(true);
    lenient().when(fiber.getStatus()).thenReturn(FiberStatus.ACTIVE);
    lenient().when(fiber.isShared()).thenReturn(false);
    lenient().when(fiber.isPure()).thenReturn(false);
    lenient()
        .when(fiber.getComposition())
        .thenReturn(
            Map.of(
                UUID.randomUUID(), new BigDecimal("50"), UUID.randomUUID(), new BigDecimal("50")));
    return fiber;
  }

  private static Fiber sharedPure() {
    Fiber fiber = mock(Fiber.class);
    lenient().when(fiber.getTenantId()).thenReturn(FiberCatalog.OWNER_ID);
    lenient().when(fiber.isShared()).thenReturn(true);
    lenient().when(fiber.isPure()).thenReturn(true);
    lenient().when(fiber.getIsActive()).thenReturn(true);
    lenient().when(fiber.getFiberName()).thenReturn("Cotton (100%)");
    return fiber;
  }

  private static Map<UUID, BigDecimal> blendComposition() {
    Map<UUID, BigDecimal> composition = new LinkedHashMap<>();
    composition.put(UUID.randomUUID(), new BigDecimal("60"));
    composition.put(UUID.randomUUID(), new BigDecimal("40"));
    return composition;
  }

  // =========================================================================
  // Reads — shared catalogue plus own rows (A01)
  // =========================================================================

  @Nested
  @DisplayName("reads")
  class Reads {

    @Test
    @DisplayName("getAll reads the tenant's own rows plus the shared catalogue in one scope")
    void getAllUsesTenantPlusOwnerScope() {
      List<Fiber> fibers = List.of(sharedPure(), ownBlend());
      when(fiberRepository.findActiveInScope(READ_SCOPE)).thenReturn(fibers);
      when(dtoAssembler.toDtos(fibers, TENANT_ID))
          .thenReturn(List.of(FiberDto.builder().build(), FiberDto.builder().build()));

      assertThat(fiberService.getAll()).hasSize(2);
      assertThat(READ_SCOPE).containsExactly(TENANT_ID, FiberCatalog.OWNER_ID);
    }

    @Test
    @DisplayName("the catalogue owner reads only its own rows, without a duplicated scope entry")
    void ownerScopeHasNoDuplicate() {
      assertThat(FiberCatalog.readScope(FiberCatalog.OWNER_ID))
          .containsExactly(FiberCatalog.OWNER_ID);
    }

    @Test
    @DisplayName("searchByName trims the query and keeps the shared scope")
    void searchTrimsQuery() {
      when(fiberRepository.searchActiveInScope(READ_SCOPE, "cot")).thenReturn(List.of());
      when(dtoAssembler.toDtos(List.of(), TENANT_ID)).thenReturn(List.of());

      assertThat(fiberService.searchByName("  cot ")).isEmpty();
    }

    @Test
    @DisplayName("another tenant's private fibre is simply not found")
    void foreignPrivateFibreIsNotFound() {
      when(fiberRepository.findByTenantIdInAndId(READ_SCOPE, FIBER_ID))
          .thenReturn(Optional.empty());

      assertThat(fiberService.getById(FIBER_ID)).isEmpty();
    }
  }

  // =========================================================================
  // Shared canonical fibres are read-only for tenants (A02)
  // =========================================================================

  @Nested
  @DisplayName("shared read-only guard")
  class SharedReadOnly {

    @Test
    @DisplayName("update of a shared canonical fibre fails with FIBER_SHARED_READ_ONLY (403)")
    void updateSharedIsForbidden() {
      Fiber shared = sharedPure();
      when(fiberRepository.findByTenantIdInAndId(READ_SCOPE, FIBER_ID))
          .thenReturn(Optional.of(shared));

      assertThatThrownBy(
              () ->
                  fiberService.updateFiber(
                      FIBER_ID, UpdateFiberRequest.builder().fiberName("Renamed").build()))
          .isInstanceOfSatisfying(
              FiberDomainException.class,
              ex -> {
                assertThat(ex.getErrorCode()).isEqualTo("FIBER_SHARED_READ_ONLY");
                assertThat(ex.getHttpStatus()).isEqualTo(403);
              });
      verify(shared, never()).update(anyString(), any());
      verify(fiberRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("source declaration on a shared canonical fibre is read-only as well")
    void declareSourceOnSharedIsForbidden() {
      Fiber shared = sharedPure();
      when(fiberRepository.findByTenantIdInAndId(READ_SCOPE, FIBER_ID))
          .thenReturn(Optional.of(shared));

      assertThatThrownBy(
              () ->
                  fiberService.updateFiber(
                      FIBER_ID,
                      UpdateFiberRequest.builder()
                          .fiberName("Cotton (100%)")
                          .materialSource(MaterialSource.RECYCLED)
                          .build()))
          .isInstanceOfSatisfying(
              FiberDomainException.class,
              ex -> assertThat(ex.getErrorCode()).isEqualTo("FIBER_SHARED_READ_ONLY"));
      verify(shared, never()).declareMaterialSource(any());
    }

    @Test
    @DisplayName("deactivation of a shared canonical fibre is forbidden and touches nothing")
    void deactivateSharedIsForbidden() {
      Fiber shared = sharedPure();
      when(fiberRepository.findByTenantIdInAndId(READ_SCOPE, FIBER_ID))
          .thenReturn(Optional.of(shared));

      assertThatThrownBy(() -> fiberService.deactivateFiber(FIBER_ID))
          .isInstanceOfSatisfying(
              FiberDomainException.class,
              ex -> assertThat(ex.getErrorCode()).isEqualTo("FIBER_SHARED_READ_ONLY"));
      verify(shared, never()).delete();
      verify(fiberUsagePort, never()).isFiberInActiveProduction(any(), any());
    }

    @Test
    @DisplayName("mutating an unknown or foreign fibre is FIBER_NOT_FOUND (404)")
    void mutatingForeignIsNotFound() {
      when(fiberRepository.findByTenantIdInAndId(READ_SCOPE, FIBER_ID))
          .thenReturn(Optional.empty());

      assertThatThrownBy(() -> fiberService.deactivateFiber(FIBER_ID))
          .isInstanceOfSatisfying(
              FiberDomainException.class,
              ex -> {
                assertThat(ex.getErrorCode()).isEqualTo("FIBER_NOT_FOUND");
                assertThat(ex.getHttpStatus()).isEqualTo(404);
              });
    }

    @Test
    @DisplayName("an inactive own fibre cannot be mutated (FIBER_INACTIVE)")
    void inactiveOwnFibreIsRejected() {
      Fiber fiber = ownBlend();
      when(fiber.getIsActive()).thenReturn(false);
      when(fiberRepository.findByTenantIdInAndId(READ_SCOPE, FIBER_ID))
          .thenReturn(Optional.of(fiber));

      assertThatThrownBy(() -> fiberService.deactivateFiber(FIBER_ID))
          .isInstanceOfSatisfying(
              FiberDomainException.class,
              ex -> assertThat(ex.getErrorCode()).isEqualTo("FIBER_INACTIVE"));
    }
  }

  // =========================================================================
  // Creation — tenants create blends; pure fibres are platform-only (A04, A05)
  // =========================================================================

  @Nested
  @DisplayName("createFiber")
  class CreateFiber {

    @Test
    @DisplayName("a tenant cannot create a pure fibre directly (platform only)")
    void tenantPureCreationIsPlatformOnly() {
      CreateFiberRequest request =
          CreateFiberRequest.builder()
              .fiberName("Cotton")
              .fiberIsoCodeId(UUID.randomUUID())
              .unit("KG")
              .build();

      assertThatThrownBy(() -> fiberService.createFiber(request))
          .isInstanceOfSatisfying(
              FiberDomainException.class,
              ex -> {
                assertThat(ex.getErrorCode()).isEqualTo("FIBER_PURE_CREATION_PLATFORM_ONLY");
                assertThat(ex.getHttpStatus()).isEqualTo(403);
              });
      verify(fiberRepository, never()).saveAndFlush(any());
      verify(productRepository, never()).save(any());
    }

    @Test
    @DisplayName("a blend with an ISO code is rejected, never silently ignored")
    void blendWithIsoIsRejected() {
      CreateFiberRequest request =
          CreateFiberRequest.builder()
              .composition(blendComposition())
              .fiberIsoCodeId(UUID.randomUUID())
              .unit("KG")
              .build();

      assertThatThrownBy(() -> fiberService.createFiber(request))
          .isInstanceOfSatisfying(
              FiberDomainException.class,
              ex -> assertThat(ex.getErrorCode()).isEqualTo("FIBER_BLEND_ISO_FORBIDDEN"));
    }

    @Test
    @DisplayName("a blend with a single material source is rejected")
    void blendWithSourceIsRejected() {
      CreateFiberRequest request =
          CreateFiberRequest.builder()
              .composition(blendComposition())
              .materialSource(MaterialSource.RECYCLED)
              .unit("KG")
              .build();

      assertThatThrownBy(() -> fiberService.createFiber(request))
          .isInstanceOfSatisfying(
              FiberDomainException.class,
              ex ->
                  assertThat(ex.getErrorCode()).isEqualTo("FIBER_BLEND_MATERIAL_SOURCE_FORBIDDEN"));
    }

    @Test
    @DisplayName("a blend outside the shared MIXED_BLEND category is rejected")
    void blendWithOtherCategoryIsRejected() {
      FiberCategory mixedBlend = mock(FiberCategory.class);
      when(mixedBlend.getId()).thenReturn(UUID.randomUUID());
      when(referenceQueryService.requireMixedBlendCategory()).thenReturn(mixedBlend);
      CreateFiberRequest request =
          CreateFiberRequest.builder()
              .composition(blendComposition())
              .fiberCategoryId(UUID.randomUUID())
              .unit("KG")
              .build();

      assertThatThrownBy(() -> fiberService.createFiber(request))
          .isInstanceOfSatisfying(
              FiberDomainException.class,
              ex -> assertThat(ex.getErrorCode()).isEqualTo("FIBER_BLEND_CATEGORY_INVALID"));
    }

    @Test
    @DisplayName("an identical active blend of the tenant is a named 409 before any insert")
    void duplicateCompositionIsRejected() {
      FiberCategory mixedBlend = mock(FiberCategory.class);
      when(referenceQueryService.requireMixedBlendCategory()).thenReturn(mixedBlend);
      Map<UUID, BigDecimal> composition = blendComposition();
      when(validationService.validateBlendDefinition(composition, TENANT_ID))
          .thenReturn(new FiberValidationService.ResolvedComposition(composition, Map.of()));
      when(fiberRepository.findActiveBlendIdByComposition(
              eq(TENANT_ID), anyString(), eq(FiberRepository.NO_FIBER)))
          .thenReturn(Optional.of(UUID.randomUUID()));

      assertThatThrownBy(
              () ->
                  fiberService.createFiber(
                      CreateFiberRequest.builder().composition(composition).unit("KG").build()))
          .isInstanceOfSatisfying(
              FiberDomainException.class,
              ex -> {
                assertThat(ex.getErrorCode()).isEqualTo("FIBER_DUPLICATE_COMPOSITION");
                assertThat(ex.getHttpStatus()).isEqualTo(409);
              });
      verify(fiberRepository).acquireCompositionLock(anyString());
      verify(fiberRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("the catalogue owner never creates blends")
    void ownerCannotCreateBlend() {
      TenantContext.setCurrentTenantId(FiberCatalog.OWNER_ID);

      assertThatThrownBy(
              () ->
                  fiberService.createFiber(
                      CreateFiberRequest.builder()
                          .composition(blendComposition())
                          .unit("KG")
                          .build()))
          .isInstanceOfSatisfying(
              FiberDomainException.class,
              ex -> assertThat(ex.getErrorCode()).isEqualTo("FIBER_CATALOG_OWNER_BLEND_FORBIDDEN"));
    }
  }

  // =========================================================================
  // updateFiber — composition (recipe) change of an own blend
  // =========================================================================

  @Nested
  @DisplayName("updateFiber — composition change")
  class UpdateFiberComposition {

    private Fiber fiber;
    private Map<UUID, BigDecimal> newComposition;
    private UpdateFiberRequest request;

    @BeforeEach
    void setUp() {
      fiber = ownBlend();
      when(fiberRepository.findByTenantIdInAndId(READ_SCOPE, FIBER_ID))
          .thenReturn(Optional.of(fiber));
      newComposition = blendComposition();
      request =
          UpdateFiberRequest.builder()
              .fiberName(FIBER_NAME)
              .composition(newComposition)
              .version(1L)
              .build();
      lenient()
          .when(validationService.validateBlendDefinition(newComposition, TENANT_ID))
          .thenReturn(new FiberValidationService.ResolvedComposition(newComposition, Map.of()));
    }

    @Test
    @DisplayName("persists the new composition when no batches are in active production")
    void noActiveBatches() {
      when(fiberUsagePort.isFiberInActiveProduction(TENANT_ID, PRODUCT_ID)).thenReturn(false);
      when(fiberRepository.findActiveBlendIdByComposition(eq(TENANT_ID), anyString(), eq(FIBER_ID)))
          .thenReturn(Optional.empty());
      when(fiberRepository.saveAndFlush(fiber)).thenReturn(fiber);
      when(dtoAssembler.toDto(fiber, TENANT_ID)).thenReturn(FiberDto.builder().build());

      assertThat(fiberService.updateFiber(FIBER_ID, request)).isNotNull();

      verify(fiber).changeBlendComposition(newComposition);
      verify(fiberRepository).saveAndFlush(fiber);
    }

    @Test
    @DisplayName("throws RecipeInUseException with structured context while batches are active")
    void activeBatches() {
      when(fiberUsagePort.isFiberInActiveProduction(TENANT_ID, PRODUCT_ID)).thenReturn(true);

      assertThatThrownBy(() -> fiberService.updateFiber(FIBER_ID, request))
          .isInstanceOfSatisfying(
              RecipeInUseException.class,
              ex -> {
                assertThat(ex.getFiberId()).isEqualTo(FIBER_ID);
                assertThat(ex.getFiberName()).isEqualTo(FIBER_NAME);
              });
      verify(fiber, never()).changeBlendComposition(any());
      verify(fiberRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("an empty composition is rejected instead of turning the blend pure")
    void emptyCompositionRejected() {
      UpdateFiberRequest clearing =
          UpdateFiberRequest.builder().fiberName(FIBER_NAME).composition(Map.of()).build();

      assertThatThrownBy(() -> fiberService.updateFiber(FIBER_ID, clearing))
          .isInstanceOfSatisfying(
              FiberDomainException.class,
              ex -> assertThat(ex.getErrorCode()).isEqualTo("FIBER_COMPOSITION_EMPTY"));
    }

    @Test
    @DisplayName("no composition in the request skips the production check entirely")
    void compositionAbsent() {
      when(fiberRepository.saveAndFlush(fiber)).thenReturn(fiber);

      fiberService.updateFiber(
          FIBER_ID, UpdateFiberRequest.builder().fiberName("Renamed").version(1L).build());

      verify(fiberUsagePort, never()).isFiberInActiveProduction(any(), any());
      verify(fiber).update("Renamed", null);
    }

    @Test
    @DisplayName("an OBSOLETE fibre cannot be updated")
    void obsoleteRejected() {
      when(fiber.getStatus()).thenReturn(FiberStatus.OBSOLETE);

      assertThatThrownBy(() -> fiberService.updateFiber(FIBER_ID, request))
          .isInstanceOfSatisfying(
              FiberDomainException.class,
              ex -> assertThat(ex.getErrorCode()).isEqualTo("FIBER_OBSOLETE"));
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
      fiber = ownBlend();
      when(fiberRepository.findByTenantIdInAndId(READ_SCOPE, FIBER_ID))
          .thenReturn(Optional.of(fiber));
    }

    @Test
    @DisplayName("marks an own fibre deleted when no batches are in active production")
    void noActiveBatches() {
      when(fiberUsagePort.isFiberInActiveProduction(TENANT_ID, PRODUCT_ID)).thenReturn(false);

      fiberService.deactivateFiber(FIBER_ID);

      verify(fiber).delete();
      verify(fiberRepository).save(fiber);
    }

    @Test
    @DisplayName("refuses with FIBER_IN_ACTIVE_PRODUCTION while batches are active")
    void activeBatches() {
      when(fiberUsagePort.isFiberInActiveProduction(TENANT_ID, PRODUCT_ID)).thenReturn(true);

      assertThatThrownBy(() -> fiberService.deactivateFiber(FIBER_ID))
          .isInstanceOfSatisfying(
              FiberDomainException.class,
              ex -> {
                assertThat(ex.getErrorCode()).isEqualTo("FIBER_IN_ACTIVE_PRODUCTION");
                assertThat(ex.getMessage()).contains(FIBER_NAME, "RESERVED or IN_PROGRESS");
              });
      verify(fiber, never()).delete();
      verify(fiberRepository, never()).save(any());
    }
  }
}
