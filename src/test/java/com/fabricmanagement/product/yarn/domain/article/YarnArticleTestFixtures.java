package com.fabricmanagement.product.yarn.domain.article;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.product.core.domain.Product;
import com.fabricmanagement.product.core.domain.ProductType;
import com.fabricmanagement.product.fiber.domain.Fiber;
import com.fabricmanagement.product.fiber.domain.FiberStatus;
import com.fabricmanagement.product.fiber.domain.MaterialSource;
import com.fabricmanagement.product.fiber.domain.reference.FiberIsoCode;
import com.fabricmanagement.product.yarn.domain.reference.YarnSpinningSystem;
import com.fabricmanagement.product.yarn.domain.reference.YarnTestMethod;
import com.fabricmanagement.product.yarn.domain.vocabulary.CountBasis;
import com.fabricmanagement.product.yarn.domain.vocabulary.CountSystem;
import com.fabricmanagement.product.yarn.domain.vocabulary.FilamentForm;
import com.fabricmanagement.product.yarn.domain.vocabulary.SpinningTechnologyFamily;
import com.fabricmanagement.product.yarn.domain.vocabulary.TwistDirection;
import com.fabricmanagement.product.yarn.domain.vocabulary.TwistStageType;
import com.fabricmanagement.product.yarn.domain.vocabulary.YarnConstructionFeature;
import com.fabricmanagement.product.yarn.domain.vocabulary.YarnMaterialForm;
import com.fabricmanagement.product.yarn.domain.vocabulary.YarnStructureType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

final class YarnArticleTestFixtures {

  static final UUID TENANT = UUID.fromString("11111111-1111-4111-8111-111111111111");
  static final YarnArticleSpecSerializer SERIALIZER =
      new YarnArticleSpecSerializer(new ObjectMapper());

  private YarnArticleTestFixtures() {}

  static Product product(boolean active) {
    Product product = mock(Product.class);
    when(product.getId()).thenReturn(UUID.fromString("22222222-2222-4222-8222-222222222222"));
    when(product.getTenantId()).thenReturn(TENANT);
    when(product.getProductType()).thenReturn(ProductType.YARN);
    when(product.getIsActive()).thenReturn(active);
    return product;
  }

  static Fiber fiber(String idSuffix, MaterialSource source) {
    return fiber(idSuffix, new AtomicReference<>(source), true);
  }

  static Fiber fiber(String idSuffix, AtomicReference<MaterialSource> source, boolean pure) {
    Fiber fiber = mock(Fiber.class);
    FiberIsoCode iso = mock(FiberIsoCode.class);
    UUID id = UUID.fromString("33333333-3333-4333-8333-" + idSuffix);
    when(fiber.getId()).thenReturn(id);
    when(fiber.getTenantId()).thenReturn(TENANT);
    when(fiber.getFiberName()).thenReturn("Fiber " + idSuffix);
    when(fiber.getFiberIsoCode()).thenReturn(iso);
    when(iso.getIsoCode()).thenReturn("CO");
    when(fiber.getMaterialSource()).thenAnswer(invocation -> source.get());
    when(fiber.isPure()).thenReturn(pure);
    when(fiber.getStatus()).thenReturn(FiberStatus.ACTIVE);
    when(fiber.getIsActive()).thenReturn(true);
    return fiber;
  }

  static YarnSpinningSystem spinningSystem(String code, SpinningTechnologyFamily family) {
    YarnSpinningSystem system = mock(YarnSpinningSystem.class);
    when(system.getId()).thenReturn(UUID.randomUUID());
    when(system.getCode()).thenReturn(code);
    when(system.getTechnologyFamily()).thenReturn(family);
    when(system.getIsActive()).thenReturn(true);
    return system;
  }

  static YarnTestMethod testMethod(String code, String propertyKey) {
    YarnTestMethod method = mock(YarnTestMethod.class);
    when(method.getId()).thenReturn(UUID.randomUUID());
    when(method.getCode()).thenReturn(code);
    when(method.getApplicablePropertyKey()).thenReturn(propertyKey);
    when(method.getIsActive()).thenReturn(true);
    return method;
  }

  static YarnArticle draft(SpecBuilder builder) {
    TenantContext.setCurrentTenantId(TENANT);
    TenantContext.setCurrentTenantUid("YARN-TEST");
    return YarnArticle.createDraft(
        product(true), "Test yarn", "fixture", builder.build(), SERIALIZER);
  }

  static SpecBuilder validSingle(Fiber fiber) {
    SpecBuilder builder = new SpecBuilder();
    builder.composition.add(new YarnArticleSpec.CompositionInput(fiber, new BigDecimal("100")));
    builder.twistStages.add(
        new YarnArticleSpec.TwistStageInput(
            TwistStageType.SINGLE, 1, TwistDirection.Z, new BigDecimal("800"), null, null));
    return builder;
  }

  static final class SpecBuilder {
    CountSystem originalCountSystem = CountSystem.TEX;
    BigDecimal originalCountValue = new BigDecimal("20");
    CountBasis countBasis = CountBasis.COMPONENT;
    YarnStructureType structureType = YarnStructureType.SINGLE;
    Integer foldCount = 1;
    Integer filamentCount;
    BigDecimal contraction;
    String sourceDesignation = "supplier wording / untouched";
    YarnMaterialForm materialForm = YarnMaterialForm.STAPLE_SPUN;
    SpinningTechnologyFamily spinningFamily = SpinningTechnologyFamily.RING;
    YarnSpinningSystem spinningSystem;
    FilamentForm filamentForm;
    Set<YarnConstructionFeature> features = new LinkedHashSet<>();
    List<YarnArticleSpec.CompositionInput> composition = new ArrayList<>();
    List<YarnArticleSpec.ComponentInput> components = new ArrayList<>();
    List<YarnArticleSpec.TwistStageInput> twistStages = new ArrayList<>();

    YarnArticleSpec build() {
      return new YarnArticleSpec(
          originalCountSystem,
          originalCountValue,
          countBasis,
          structureType,
          foldCount,
          filamentCount,
          contraction,
          sourceDesignation,
          materialForm,
          spinningFamily,
          spinningSystem,
          filamentForm,
          features,
          composition,
          components,
          twistStages);
    }
  }
}
