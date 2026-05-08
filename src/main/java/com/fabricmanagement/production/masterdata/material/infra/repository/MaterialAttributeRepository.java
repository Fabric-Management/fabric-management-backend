package com.fabricmanagement.production.masterdata.material.infra.repository;

import com.fabricmanagement.production.masterdata.material.domain.reference.MaterialAttribute;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MaterialAttributeRepository extends JpaRepository<MaterialAttribute, UUID> {

  List<MaterialAttribute> findByIsActiveTrue();

  List<MaterialAttribute> findByIsActiveTrueAndMaterialScopeIn(List<String> scopes);

  Optional<MaterialAttribute> findByAttributeCode(String attributeCode);
}
