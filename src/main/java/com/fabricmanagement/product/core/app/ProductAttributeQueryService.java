package com.fabricmanagement.product.core.app;

import com.fabricmanagement.product.core.domain.reference.ProductAttribute;
import com.fabricmanagement.product.core.infra.repository.ProductAttributeRepository;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read boundary for cross-module product-attribute references. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductAttributeQueryService {

  private final ProductAttributeRepository productAttributeRepository;

  public Optional<ProductAttribute> findByTenantAndId(UUID tenantId, UUID attributeId) {
    return productAttributeRepository
        .findById(attributeId)
        .filter(attribute -> tenantId.equals(attribute.getTenantId()));
  }
}
