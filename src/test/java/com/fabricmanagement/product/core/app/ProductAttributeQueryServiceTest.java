package com.fabricmanagement.product.core.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.fabricmanagement.product.core.domain.reference.ProductAttribute;
import com.fabricmanagement.product.core.infra.repository.ProductAttributeRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProductAttributeQueryServiceTest {

  @Mock private ProductAttributeRepository repository;
  @InjectMocks private ProductAttributeQueryService queryService;

  @Test
  void findByTenantAndIdReturnsOnlyTenantOwnedAttribute() {
    UUID tenantId = UUID.randomUUID();
    UUID attributeId = UUID.randomUUID();
    ProductAttribute attribute = ProductAttribute.builder().attributeCode("ORGANIC").build();
    attribute.setTenantId(tenantId);
    when(repository.findById(attributeId)).thenReturn(Optional.of(attribute));

    assertThat(queryService.findByTenantAndId(tenantId, attributeId)).contains(attribute);
    assertThat(queryService.findByTenantAndId(UUID.randomUUID(), attributeId)).isEmpty();
  }
}
