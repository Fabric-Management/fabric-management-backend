package com.fabricmanagement.product.fiber.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.product.fiber.domain.Fiber;
import com.fabricmanagement.product.fiber.domain.FiberCatalog;
import com.fabricmanagement.product.fiber.domain.FiberQualityStandard;
import com.fabricmanagement.product.fiber.domain.FiberQualityTargetType;
import com.fabricmanagement.product.fiber.domain.exception.FiberDomainException;
import com.fabricmanagement.product.fiber.domain.reference.FiberIsoCode;
import com.fabricmanagement.product.fiber.dto.CreateFiberQualityStandardRequest;
import com.fabricmanagement.product.fiber.dto.CreateFiberQualityStandardRequest.CreateFiberQualityStandardRequestBuilder;
import com.fabricmanagement.product.fiber.dto.FiberQualityStandardDto;
import com.fabricmanagement.product.fiber.dto.UpdateFiberQualityStandardRequest;
import com.fabricmanagement.product.fiber.infra.repository.FiberQualityStandardRepository;
import com.fabricmanagement.product.fiber.infra.repository.FiberRepository;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Target-typed quality profile rules (FIBER-CATALOG-1 §8, A11/A12/A15 unit level). */
@ExtendWith(MockitoExtension.class)
class FiberQualityStandardServiceTest {

  private static final UUID TENANT_ID = UUID.randomUUID();
  private static final UUID ISO_CODE_ID = UUID.randomUUID();
  private static final UUID STANDARD_ID = UUID.randomUUID();
  private static final UUID FIBER_ID = UUID.randomUUID();

  @Mock private FiberQualityStandardRepository standardRepository;
  @Mock private FiberReferenceQueryService referenceQueryService;
  @Mock private FiberRepository fiberRepository;
  @Mock private FiberQualityQueryService qualityQueryService;
  @Mock private FiberIsoCode isoCode;

  private FiberQualityStandardService service;

  @BeforeEach
  void setUp() {
    TenantContext.setCurrentTenantId(TENANT_ID);
    service =
        new FiberQualityStandardService(
            standardRepository, referenceQueryService, fiberRepository, qualityQueryService);
    lenient().when(isoCode.getId()).thenReturn(ISO_CODE_ID);
    lenient().when(isoCode.getIsActive()).thenReturn(true);
    lenient()
        .when(referenceQueryService.findIsoCodeById(ISO_CODE_ID))
        .thenReturn(Optional.of(isoCode));
    lenient()
        .when(standardRepository.saveAndFlush(any(FiberQualityStandard.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  private static CreateFiberQualityStandardRequestBuilder iso() {
    return CreateFiberQualityStandardRequest.builder()
        .targetType(FiberQualityTargetType.ISO_CODE)
        .isoCodeId(ISO_CODE_ID)
        .standardName("Cotton standard");
  }

  private static CreateFiberQualityStandardRequestBuilder fiber() {
    return CreateFiberQualityStandardRequest.builder()
        .targetType(FiberQualityTargetType.FIBER)
        .fiberId(FIBER_ID)
        .standardName("Blend standard")
        .moisturePctMax(8.0);
  }

  private Fiber visibleFiber(boolean pure, Map<UUID, BigDecimal> composition) {
    Fiber target = mock(Fiber.class);
    lenient().when(target.getId()).thenReturn(FIBER_ID);
    lenient().when(target.getIsActive()).thenReturn(true);
    lenient().when(target.isPure()).thenReturn(pure);
    lenient().when(target.getComposition()).thenReturn(composition);
    when(fiberRepository.findByTenantIdInAndId(FiberCatalog.readScope(TENANT_ID), FIBER_ID))
        .thenReturn(Optional.of(target));
    return target;
  }

  @Test
  void createsIsoProfileAndReturnsUniformityToleranceFields() {
    FiberQualityStandardDto created =
        service.create(
            iso()
                .uniformityIndexMin(80.0)
                .uniformityIndexTarget(84.0)
                .uniformityIndexMax(86.0)
                .build());

    assertThat(created.getTargetType()).isEqualTo(FiberQualityTargetType.ISO_CODE);
    assertThat(created.getUniformityIndexMin()).isEqualTo(80.0);
    assertThat(created.getUniformityIndexTarget()).isEqualTo(84.0);
    assertThat(created.getUniformityIndexMax()).isEqualTo(86.0);
  }

  @Test
  void fiberProfileOfABlendCapturesItsNormalisedComposition() {
    UUID cotton = UUID.randomUUID();
    UUID polyester = UUID.randomUUID();
    Map<UUID, BigDecimal> composition = new LinkedHashMap<>();
    composition.put(cotton, new BigDecimal("60.00"));
    composition.put(polyester, new BigDecimal("40.0"));
    visibleFiber(false, composition);

    service.create(fiber().build());

    ArgumentCaptor<FiberQualityStandard> saved =
        ArgumentCaptor.forClass(FiberQualityStandard.class);
    verify(standardRepository).saveAndFlush(saved.capture());
    assertThat(saved.getValue().getTargetType()).isEqualTo(FiberQualityTargetType.FIBER);
    assertThat(saved.getValue().getFiberId()).isEqualTo(FIBER_ID);
    assertThat(saved.getValue().getIsoCodeId()).isNull();
    assertThat(saved.getValue().getTargetComposition())
        .containsEntry(cotton, new BigDecimal("60"))
        .containsEntry(polyester, new BigDecimal("40"))
        .hasSize(2);
  }

  @Test
  void fiberProfileOfAPureFibreCapturesItselfAtOneHundred() {
    visibleFiber(true, Map.of());

    service.create(fiber().build());

    ArgumentCaptor<FiberQualityStandard> saved =
        ArgumentCaptor.forClass(FiberQualityStandard.class);
    verify(standardRepository).saveAndFlush(saved.capture());
    assertThat(saved.getValue().getTargetComposition())
        .containsExactly(Map.entry(FIBER_ID, new BigDecimal("100")));
  }

  @Test
  void fiberProfileForAnInvisibleFibreIsNotFound() {
    when(fiberRepository.findByTenantIdInAndId(FiberCatalog.readScope(TENANT_ID), FIBER_ID))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.create(fiber().build()))
        .isInstanceOf(FiberDomainException.class)
        .extracting("errorCode")
        .isEqualTo("FIBER_NOT_FOUND");
  }

  @Test
  void targetFieldsMustMatchTheTargetType() {
    assertThatThrownBy(() -> service.create(iso().fiberId(FIBER_ID).moisturePctMax(8.0).build()))
        .isInstanceOf(FiberDomainException.class)
        .extracting("errorCode")
        .isEqualTo("FIBER_QUALITY_TARGET_INVALID");
    assertThatThrownBy(() -> service.create(fiber().isoCodeId(ISO_CODE_ID).build()))
        .isInstanceOf(FiberDomainException.class)
        .extracting("errorCode")
        .isEqualTo("FIBER_QUALITY_TARGET_INVALID");
    verify(standardRepository, never()).saveAndFlush(any());
  }

  @Test
  void aProfileWithoutAnyCriterionIsRejected() {
    assertThatThrownBy(() -> service.create(iso().build()))
        .isInstanceOf(FiberDomainException.class)
        .extracting("errorCode")
        .isEqualTo("FIBER_QUALITY_STANDARD_EMPTY");
    verify(standardRepository, never()).saveAndFlush(any());
  }

  @Test
  void updatesThresholdsButNeverTheTarget() {
    FiberQualityStandard existing = FiberQualityStandard.forIsoCode(isoCode, "Cotton standard");
    existing.setId(STANDARD_ID);
    existing.setMoisturePctMax(8.0);
    when(standardRepository.findByTenantIdAndIdAndIsActiveTrue(TENANT_ID, STANDARD_ID))
        .thenReturn(Optional.of(existing));

    FiberQualityStandardDto updated =
        service.update(
            STANDARD_ID,
            UpdateFiberQualityStandardRequest.builder()
                .isoCodeId(ISO_CODE_ID)
                .standardName("Cotton standard")
                .uniformityIndexMin(81.0)
                .uniformityIndexTarget(84.5)
                .uniformityIndexMax(87.0)
                .build());

    assertThat(updated.getUniformityIndexMin()).isEqualTo(81.0);
    assertThat(updated.getUniformityIndexTarget()).isEqualTo(84.5);
    assertThat(updated.getUniformityIndexMax()).isEqualTo(87.0);

    assertThatThrownBy(
            () ->
                service.update(
                    STANDARD_ID,
                    UpdateFiberQualityStandardRequest.builder()
                        .targetType(FiberQualityTargetType.FIBER)
                        .fiberId(FIBER_ID)
                        .standardName("Cotton standard")
                        .moisturePctMax(8.0)
                        .build()))
        .isInstanceOf(FiberDomainException.class)
        .extracting("errorCode")
        .isEqualTo("FIBER_QUALITY_TARGET_IMMUTABLE");
  }

  @Test
  void settingADefaultLocksTheTargetAndClearsThePreviousDefaultFirst() {
    FiberQualityStandard previous = FiberQualityStandard.forIsoCode(isoCode, "Old default");
    previous.setId(UUID.randomUUID());
    previous.setIsDefault(true);
    FiberQualityStandard next = FiberQualityStandard.forIsoCode(isoCode, "New default");
    next.setId(STANDARD_ID);
    next.setMoisturePctMax(8.0);
    when(standardRepository.findByTenantIdAndIdAndIsActiveTrue(TENANT_ID, STANDARD_ID))
        .thenReturn(Optional.of(next));
    when(standardRepository.findByTenantIdAndTargetTypeAndIsoCode_IdAndIsDefaultTrueAndIsActiveTrue(
            TENANT_ID, FiberQualityTargetType.ISO_CODE, ISO_CODE_ID))
        .thenReturn(Optional.of(previous));

    service.setDefault(STANDARD_ID);

    InOrder order = inOrder(standardRepository);
    order.verify(standardRepository).acquireTargetLock(anyString());
    order.verify(standardRepository).saveAndFlush(previous);
    order.verify(standardRepository).saveAndFlush(next);
    assertThat(previous.getIsDefault()).isFalse();
    assertThat(next.getIsDefault()).isTrue();
  }

  @ParameterizedTest
  @MethodSource("invalidUniformityRanges")
  void rejectsInvalidUniformityToleranceRanges(
      Double min, Double target, Double max, String expectedMessage) {
    CreateFiberQualityStandardRequest request =
        iso()
            .standardName("Invalid cotton standard")
            .uniformityIndexMin(min)
            .uniformityIndexTarget(target)
            .uniformityIndexMax(max)
            .build();

    assertThatThrownBy(() -> service.create(request))
        .isInstanceOf(FiberDomainException.class)
        .hasMessageContaining(expectedMessage);
    verify(standardRepository, never()).saveAndFlush(any());
  }

  private static Stream<Arguments> invalidUniformityRanges() {
    return Stream.of(
        Arguments.of(90.0, null, 80.0, "min"),
        Arguments.of(80.0, 79.0, 90.0, "below min"),
        Arguments.of(80.0, 91.0, 90.0, "exceed max"));
  }
}
