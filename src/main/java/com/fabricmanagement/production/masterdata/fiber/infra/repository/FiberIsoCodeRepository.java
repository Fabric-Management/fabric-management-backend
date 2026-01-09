package com.fabricmanagement.production.masterdata.fiber.infra.repository;

import com.fabricmanagement.production.masterdata.fiber.domain.reference.FiberIsoCode;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FiberIsoCodeRepository extends JpaRepository<FiberIsoCode, UUID> {

  List<FiberIsoCode> findByIsActiveTrue();

  Optional<FiberIsoCode> findByIsoCode(String isoCode);
}
