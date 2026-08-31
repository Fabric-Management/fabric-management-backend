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
import com.fabricmanagement.product.fiber.domain.MaterialSource;
import com.fabricmanagement.product.fiber.domain.event.FiberMaterialSourceDeclaredEvent;
import com.fabricmanagement.product.fiber.domain.exception.FiberDomainException;
import com.fabricmanagement.product.fiber.domain.reference.FiberCategory;
import com.fabricmanagement.product.fiber.domain.reference.FiberIsoCode;
import com.fabricmanagement.product.fiber.dto.CreateFiberRequest;
import com.fabricmanagement.product.fiber.dto.FiberDto;
import com.fabricmanagement.product.fiber.dto.UpdateFiberRequest;
import com.fabricmanagement.product.fiber.infra.repository.FiberCategoryRepository;
import com.fabricmanagement.product.fiber.infra.repository.FiberIsoCodeRepository;
import com.fabricmanagement.product.fiber.infra.repository.FiberRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FiberMaterialSourceServiceTest {

  @Mock private FiberRepository fiberRepository;
  @Mock private ProductRepository productRepository;
  @Mock private FiberCategoryRepository fiberCategoryRepository;
  @Mock private FiberIsoCodeRepository fiberIsoCodeRepository;
  @Mock private DomainEventPublisher eventPublisher;
  @Mock private FiberValidationService validationService;
  @Mock private FiberUsagePort fiberUsagePort;

  @InjectMocks private FiberService fiberService;

  @AfterEach
  void clearContext() {
    TenantContext.clear();
  }

  @ParameterizedTest
  @NullSource
  @EnumSource(MaterialSource.class)
  void directPureCreationThreadsOptionalSourceThroughThePureFactory(MaterialSource source) {
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
            TenantContext.TEMPLATE_TENANT_ID, "TEMPLATE", UUID.randomUUID(), null));
    when(productRepository.findByTenantIdAndId(TenantContext.TEMPLATE_TENANT_ID, productId))
        .thenReturn(Optional.of(product));
    when(fiberRepository.findByProductId(productId)).thenReturn(Optional.empty());
    when(fiberCategoryRepository.findById(categoryId)).thenReturn(Optional.of(category));
    when(fiberIsoCodeRepository.findById(isoId)).thenReturn(Optional.of(isoCode));
    when(fiberRepository.save(any(Fiber.class)))
        .thenAnswer(
            invocation -> {
              Fiber fiber = invocation.getArgument(0);
              fiber.setId(UUID.randomUUID());
              fiber.setTenantId(TenantContext.TEMPLATE_TENANT_ID);
              return fiber;
            });

    FiberDto created =
        fiberService.createFiber(
            CreateFiberRequest.builder()
                .productId(productId)
                .fiberCategoryId(categoryId)
                .fiberIsoCodeId(isoId)
                .fiberName("Polyester")
                .materialSource(source)
                .build());

    assertThat(created.getMaterialSource()).isEqualTo(source);
  }

  @Test
  void updateNullIsNoOpWhileDeclarationPublishesActorExplicitly() {
    UUID tenantId = UUID.randomUUID();
    UUID actorId = UUID.randomUUID();
    UUID fiberId = UUID.randomUUID();
    Fiber fiber = mock(Fiber.class);
    when(fiber.getVersion()).thenReturn(0L);
    when(fiber.getTenantId()).thenReturn(tenantId);
    when(fiber.getId()).thenReturn(fiberId);
    when(fiberRepository.findByTenantIdInAndId(
            List.of(tenantId, TenantContext.TEMPLATE_TENANT_ID), fiberId))
        .thenReturn(Optional.of(fiber));
    when(fiberRepository.save(fiber)).thenReturn(fiber);
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
    CreateFiberRequest request =
        CreateFiberRequest.builder()
            .fiberName("Invalid Blend")
            .materialSource(MaterialSource.VIRGIN)
            .composition(Map.of(UUID.randomUUID(), new BigDecimal("100.00")))
            .build();

    assertThatThrownBy(() -> fiberService.createFiber(request))
        .isInstanceOf(FiberDomainException.class)
        .extracting("errorCode")
        .isEqualTo("FIBER_BLEND_MATERIAL_SOURCE_FORBIDDEN");
  }
}
