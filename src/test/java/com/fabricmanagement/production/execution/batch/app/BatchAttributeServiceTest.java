package com.fabricmanagement.production.execution.batch.app;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.production.execution.batch.domain.Batch;
import com.fabricmanagement.production.execution.batch.domain.BatchAttribute;
import com.fabricmanagement.production.execution.batch.domain.exception.BatchDomainException;
import com.fabricmanagement.production.execution.batch.dto.AddBatchAttributeRequest;
import com.fabricmanagement.production.execution.batch.infra.repository.BatchAttributeRepository;
import com.fabricmanagement.production.execution.batch.infra.repository.BatchRepository;
import com.fabricmanagement.production.masterdata.product.domain.reference.ProductAttribute;
import com.fabricmanagement.production.masterdata.product.infra.repository.ProductAttributeRepository;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BatchAttributeServiceTest {

  private static final UUID TENANT_ID = UUID.randomUUID();
  private static final UUID BATCH_ID = UUID.randomUUID();
  private static final UUID ATTRIBUTE_ID = UUID.randomUUID();
  private static final UUID BATCH_ATTRIBUTE_ID = UUID.randomUUID();

  @Mock private BatchAttributeRepository attributeRepository;
  @Mock private BatchRepository batchRepository;
  @Mock private ProductAttributeRepository productAttributeRepository;

  private BatchAttributeService service;
  private Batch batch;

  @BeforeEach
  void setUp() {
    TenantContext.setCurrentTenantId(TENANT_ID);
    service =
        new BatchAttributeService(attributeRepository, batchRepository, productAttributeRepository);
    batch = Batch.builder().batchCode("LOT-1").build();
    batch.setId(BATCH_ID);
    batch.setTenantId(TENANT_ID);
    when(batchRepository.findByIdAndTenantId(BATCH_ID, TENANT_ID)).thenReturn(Optional.of(batch));
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @ParameterizedTest
  @MethodSource("legacyCodes")
  void add_rejectsEveryLegacyColorCode(String attributeCode) {
    ProductAttribute attribute = attribute(attributeCode);
    when(productAttributeRepository.findById(ATTRIBUTE_ID)).thenReturn(Optional.of(attribute));

    assertLegacyWriteRejected(
        () -> service.add(BATCH_ID, new AddBatchAttributeRequest(ATTRIBUTE_ID, "value")));

    verify(attributeRepository, never()).save(any());
  }

  @ParameterizedTest
  @MethodSource("legacyCodes")
  void delete_rejectsEveryLegacyColorCode(String attributeCode) {
    BatchAttribute entity =
        BatchAttribute.builder()
            .batch(batch)
            .attribute(attribute(attributeCode))
            .value("value")
            .build();
    entity.setId(BATCH_ATTRIBUTE_ID);
    entity.setTenantId(TENANT_ID);
    when(attributeRepository.findByIdAndBatch_IdAndTenantId(
            BATCH_ATTRIBUTE_ID, BATCH_ID, TENANT_ID))
        .thenReturn(Optional.of(entity));

    assertLegacyWriteRejected(() -> service.delete(BATCH_ID, BATCH_ATTRIBUTE_ID));

    verify(attributeRepository, never()).save(any());
  }

  @Test
  void add_allowsNonColorAttribute() {
    ProductAttribute attribute = attribute("ORGANIC");
    when(productAttributeRepository.findById(ATTRIBUTE_ID)).thenReturn(Optional.of(attribute));
    when(attributeRepository.findByBatch_IdAndAttribute_Id(BATCH_ID, ATTRIBUTE_ID))
        .thenReturn(Optional.empty());
    when(attributeRepository.save(any(BatchAttribute.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    assertThatCode(() -> service.add(BATCH_ID, new AddBatchAttributeRequest(ATTRIBUTE_ID, "true")))
        .doesNotThrowAnyException();

    verify(attributeRepository).save(any(BatchAttribute.class));
  }

  private void assertLegacyWriteRejected(
      org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
    assertThatThrownBy(call)
        .isInstanceOf(BatchDomainException.class)
        .extracting("errorCode", "httpStatus")
        .containsExactly(BatchAttributeService.LEGACY_COLOR_ATTRIBUTE_WRITE, 409);
  }

  private ProductAttribute attribute(String code) {
    ProductAttribute attribute = ProductAttribute.builder().attributeCode(code).build();
    attribute.setId(ATTRIBUTE_ID);
    attribute.setTenantId(TENANT_ID);
    return attribute;
  }

  private static Stream<Arguments> legacyCodes() {
    return Stream.of(
        Arguments.of(" color "),
        Arguments.of("COLOUR"),
        Arguments.of("COLOR_ID"),
        Arguments.of("COLOUR_ID"),
        Arguments.of("Shade"));
  }
}
