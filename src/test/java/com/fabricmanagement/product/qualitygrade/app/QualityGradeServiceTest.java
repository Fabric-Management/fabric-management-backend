package com.fabricmanagement.product.qualitygrade.app;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.product.core.domain.ProductType;
import com.fabricmanagement.product.qualitygrade.domain.exception.QualityGradeDomainException;
import com.fabricmanagement.product.qualitygrade.infra.repository.QualityGradeRepository;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class QualityGradeServiceTest {

  private static final UUID TENANT_ID = UUID.randomUUID();

  @Mock private QualityGradeRepository qualityGradeRepository;
  @InjectMocks private QualityGradeService qualityGradeService;

  @BeforeEach
  void setUp() {
    TenantContext.setCurrentTenantId(TENANT_ID);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void duplicateGradeUsesProductDomainContractWithoutChangingMessageOrStatus() {
    when(qualityGradeRepository.existsByTenantIdAndProductTypeAndCode(
            TENANT_ID, ProductType.FIBER, "1A"))
        .thenReturn(true);

    assertThatThrownBy(
            () ->
                qualityGradeService.create(
                    ProductType.FIBER,
                    "1A",
                    "First Quality",
                    1,
                    BigDecimal.ONE,
                    true,
                    false,
                    "#FFFFFF",
                    true))
        .isInstanceOf(QualityGradeDomainException.class)
        .hasMessage("QualityGrade already exists: productType=FIBER, code=1A")
        .extracting("errorCode", "httpStatus")
        .containsExactly("PRODUCT_RULE_VIOLATION", 400);
  }
}
