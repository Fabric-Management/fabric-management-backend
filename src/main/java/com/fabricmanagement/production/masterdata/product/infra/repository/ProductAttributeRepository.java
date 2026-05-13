package com.fabricmanagement.production.masterdata.product.infra.repository;

import com.fabricmanagement.production.masterdata.product.domain.reference.ProductAttribute;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductAttributeRepository extends JpaRepository<ProductAttribute, UUID> {

  List<ProductAttribute> findByIsActiveTrue();

  List<ProductAttribute> findByIsActiveTrueAndProductScopeIn(List<String> scopes);

  Optional<ProductAttribute> findByAttributeCode(String attributeCode);
}
