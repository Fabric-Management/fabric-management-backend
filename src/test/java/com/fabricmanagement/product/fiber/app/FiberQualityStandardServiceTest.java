package com.fabricmanagement.product.fiber.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.product.fiber.domain.FiberQualityStandard;
import com.fabricmanagement.product.fiber.domain.exception.FiberDomainException;
import com.fabricmanagement.product.fiber.domain.reference.FiberIsoCode;
import com.fabricmanagement.product.fiber.dto.CreateFiberQualityStandardRequest;
import com.fabricmanagement.product.fiber.dto.FiberQualityStandardDto;
import com.fabricmanagement.product.fiber.dto.UpdateFiberQualityStandardRequest;
import com.fabricmanagement.product.fiber.infra.repository.FiberIsoCodeRepository;
import com.fabricmanagement.product.fiber.infra.repository.FiberQualityStandardRepository;
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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FiberQualityStandardServiceTest {

  private static final UUID TENANT_ID = UUID.randomUUID();
  private static final UUID ISO_CODE_ID = UUID.randomUUID();
  private static final UUID STANDARD_ID = UUID.randomUUID();

  @Mock private FiberQualityStandardRepository standardRepository;
  @Mock private FiberIsoCodeRepository fiberIsoCodeRepository;
  @Mock private FiberIsoCode isoCode;

  @InjectMocks private FiberQualityStandardService service;

  @BeforeEach
  void setUp() {
    TenantContext.setCurrentTenantId(TENANT_ID);
    when(fiberIsoCodeRepository.findById(ISO_CODE_ID)).thenReturn(Optional.of(isoCode));
    lenient().when(isoCode.getId()).thenReturn(ISO_CODE_ID);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void createsAndReturnsUniformityToleranceFields() {
    when(standardRepository.save(any(FiberQualityStandard.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    CreateFiberQualityStandardRequest request =
        CreateFiberQualityStandardRequest.builder()
            .isoCodeId(ISO_CODE_ID)
            .standardName("Cotton standard")
            .uniformityIndexMin(80.0)
            .uniformityIndexTarget(84.0)
            .uniformityIndexMax(86.0)
            .build();

    FiberQualityStandardDto created = service.create(request);

    assertThat(created.getUniformityIndexMin()).isEqualTo(80.0);
    assertThat(created.getUniformityIndexTarget()).isEqualTo(84.0);
    assertThat(created.getUniformityIndexMax()).isEqualTo(86.0);
  }

  @Test
  void updatesAndReturnsUniformityToleranceFields() {
    FiberQualityStandard existing =
        FiberQualityStandard.builder().isoCode(isoCode).standardName("Cotton standard").build();
    when(standardRepository.findByTenantIdAndId(TENANT_ID, STANDARD_ID))
        .thenReturn(Optional.of(existing));
    when(standardRepository.save(existing)).thenReturn(existing);
    UpdateFiberQualityStandardRequest request =
        UpdateFiberQualityStandardRequest.builder()
            .isoCodeId(ISO_CODE_ID)
            .standardName("Cotton standard")
            .uniformityIndexMin(81.0)
            .uniformityIndexTarget(84.5)
            .uniformityIndexMax(87.0)
            .build();

    FiberQualityStandardDto updated = service.update(STANDARD_ID, request);

    assertThat(updated.getUniformityIndexMin()).isEqualTo(81.0);
    assertThat(updated.getUniformityIndexTarget()).isEqualTo(84.5);
    assertThat(updated.getUniformityIndexMax()).isEqualTo(87.0);
  }

  @ParameterizedTest
  @MethodSource("invalidUniformityRanges")
  void rejectsInvalidUniformityToleranceRanges(
      Double min, Double target, Double max, String expectedMessage) {
    CreateFiberQualityStandardRequest request =
        CreateFiberQualityStandardRequest.builder()
            .isoCodeId(ISO_CODE_ID)
            .standardName("Invalid cotton standard")
            .uniformityIndexMin(min)
            .uniformityIndexTarget(target)
            .uniformityIndexMax(max)
            .build();

    assertThatThrownBy(() -> service.create(request))
        .isInstanceOf(FiberDomainException.class)
        .hasMessageContaining(expectedMessage);
    verify(standardRepository, never()).save(any());
  }

  private static Stream<Arguments> invalidUniformityRanges() {
    return Stream.of(
        Arguments.of(90.0, null, 80.0, "min"),
        Arguments.of(80.0, 79.0, 90.0, "below min"),
        Arguments.of(80.0, 91.0, 90.0, "exceed max"));
  }
}
