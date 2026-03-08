package com.fabricmanagement.production.masterdata.fiber.infra.repository;

import com.fabricmanagement.production.masterdata.fiber.domain.reference.FiberAttribute;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FiberAttributeRepository extends JpaRepository<FiberAttribute, UUID> {

  List<FiberAttribute> findByIsActiveTrue();

  Optional<FiberAttribute> findByAttributeCode(String attributeCode);
}
