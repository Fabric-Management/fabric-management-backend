package com.fabricmanagement.product.fiber.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.product.core.domain.Product;
import com.fabricmanagement.product.core.domain.ProductType;
import com.fabricmanagement.product.core.infra.repository.ProductRepository;
import com.fabricmanagement.product.fiber.app.port.FiberUsagePort;
import com.fabricmanagement.product.fiber.domain.Fiber;
import com.fabricmanagement.product.fiber.domain.FiberCatalog;
import com.fabricmanagement.product.fiber.domain.FiberStatus;
import com.fabricmanagement.product.fiber.domain.MaterialSource;
import com.fabricmanagement.product.fiber.domain.event.FiberMaterialSourceDeclaredEvent;
import com.fabricmanagement.product.fiber.domain.exception.FiberDomainException;
import com.fabricmanagement.product.fiber.domain.reference.FiberCategory;
import com.fabricmanagement.product.fiber.domain.reference.FiberIsoCode;
import com.fabricmanagement.product.fiber.dto.CreateFiberRequest;
import com.fabricmanagement.product.fiber.dto.UpdateFiberRequest;
import com.fabricmanagement.product.fiber.infra.repository.FiberRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FiberMaterialSourceServiceTest {

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
  void clearContext() {
    TenantContext.clear();
  }

  @Test
  void ownerPublishesCanonicalPureFibresWithAnUndeclaredSource() {
    UUID productId = UUID.randomUUID();
    UUID categoryId = UUID.randomUUID();
    UUID isoId = UUID.randomUUID();
    Product product = Product.create(ProductType.FIBER, "KG");
    product.setId(productId);
    FiberCategory category =
        FiberCategory.builder().categoryCode("SYNTHETIC_POLYMER").categoryName("Synthetic").build();
    category.setId(categoryId);
    FiberIsoCode isoCode =
        FiberIsoCode.builder()
            .isoCode("PES")
            .fiberName("Polyester")
            .fiberType("SYNTHETIC_POLYMER")
            .isOfficialIso(true)
            .build();
    isoCode.setId(isoId);
    TenantContext.restore(
        new TenantContext.TenantSnapshot(
            FiberCatalog.OWNER_ID, "TEMPLATE", UUID.randomUUID(), null));
    when(productRepository.findByTenantIdAndId(FiberCatalog.OWNER_ID, productId))
        .thenReturn(Optional.of(product));
    when(fiberRepository.findInScopeByProductId(List.of(FiberCatalog.OWNER_ID), productId))
        .thenReturn(Optional.empty());
    when(referenceQueryService.findCategoryById(categoryId)).thenReturn(Optional.of(category));
    when(referenceQueryService.findIsoCodeById(isoId)).thenReturn(Optional.of(isoCode));
    when(fiberRepository.saveAndFlush(any(Fiber.class)))
        .thenAnswer(
            invocation -> {
              Fiber fiber = invocation.getArgument(0);
              fiber.setId(UUID.randomUUID());
              fiber.setTenantId(FiberCatalog.OWNER_ID);
              return fiber;
            });

    fiberService.createFiber(
        CreateFiberRequest.builder()
            .productId(productId)
            .fiberCategoryId(categoryId)
            .fiberIsoCodeId(isoId)
            .fiberName("Polyester (100%)")
            .build());

    ArgumentCaptor<Fiber> saved = ArgumentCaptor.forClass(Fiber.class);
    verify(fiberRepository).saveAndFlush(saved.capture());
    assertThat(saved.getValue().getMaterialSource()).isNull();
    assertThat(saved.getValue().getFiberIsoCode()).isSameAs(isoCode);
  }

  @ParameterizedTest
  @EnumSource(MaterialSource.class)
  void canonicalPublicationRejectsAnyMaterialSource(MaterialSource source) {
    TenantContext.restore(
        new TenantContext.TenantSnapshot(
            FiberCatalog.OWNER_ID, "TEMPLATE", UUID.randomUUID(), null));

    assertThatThrownBy(
            () ->
                fiberService.createFiber(
                    CreateFiberRequest.builder()
                        .fiberName("Polyester (100%)")
                        .fiberIsoCodeId(UUID.randomUUID())
                        .materialSource(source)
                        .unit("KG")
                        .build()))
        .isInstanceOf(FiberDomainException.class)
        .extracting("errorCode")
        .isEqualTo("FIBER_CANONICAL_SOURCE_FORBIDDEN");
    verify(fiberRepository, never()).saveAndFlush(any());
  }

  @Test
  void updateNullIsNoOpWhileDeclarationOnAnOwnLegacyFibrePublishesActorExplicitly() {
    UUID tenantId = UUID.randomUUID();
    UUID actorId = UUID.randomUUID();
    UUID fiberId = UUID.randomUUID();
    Fiber fiber = mock(Fiber.class);
    when(fiber.getVersion()).thenReturn(0L);
    when(fiber.getTenantId()).thenReturn(tenantId);
    when(fiber.getId()).thenReturn(fiberId);
    when(fiber.isShared()).thenReturn(false);
    when(fiber.getIsActive()).thenReturn(true);
    when(fiber.getStatus()).thenReturn(FiberStatus.ACTIVE);
    when(fiberRepository.findByTenantIdInAndId(FiberCatalog.readScope(tenantId), fiberId))
        .thenReturn(Optional.of(fiber));
    when(fiberRepository.saveAndFlush(fiber)).thenReturn(fiber);
    TenantContext.restore(new TenantContext.TenantSnapshot(tenantId, "TENANT", actorId, null));

    fiberService.updateFiber(
        fiberId, UpdateFiberRequest.builder().fiberName("Same Fiber").version(0L).build());

    verify(fiber, never()).declareMaterialSource(any());
    verify(eventPublisher, never()).publish(any(FiberMaterialSourceDeclaredEvent.class));

    fiberService.updateFiber(
        fiberId,
        UpdateFiberRequest.builder()
            .fiberName("Same Fiber")
            .materialSource(MaterialSource.RECYCLED)
            .version(0L)
            .build());

    verify(fiber).declareMaterialSource(MaterialSource.RECYCLED);
    ArgumentCaptor<FiberMaterialSourceDeclaredEvent> event =
        ArgumentCaptor.forClass(FiberMaterialSourceDeclaredEvent.class);
    verify(eventPublisher).publish(event.capture());
    assertThat(event.getValue().getTenantId()).isEqualTo(tenantId);
    assertThat(event.getValue().getFiberId()).isEqualTo(fiberId);
    assertThat(event.getValue().getActorId()).isEqualTo(actorId);
  }

  @Test
  void createRejectsOneSourceForAComposition() {
    TenantContext.setCurrentTenantId(UUID.randomUUID());
    CreateFiberRequest request =
        CreateFiberRequest.builder()
            .fiberName("Invalid Blend")
            .materialSource(MaterialSource.VIRGIN)
            .composition(
                Map.of(
                    UUID.randomUUID(),
                    new BigDecimal("60"),
                    UUID.randomUUID(),
                    new BigDecimal("40")))
            .build();

    assertThatThrownBy(() -> fiberService.createFiber(request))
        .isInstanceOf(FiberDomainException.class)
        .extracting("errorCode")
        .isEqualTo("FIBER_BLEND_MATERIAL_SOURCE_FORBIDDEN");
  }
}
