package com.fabricmanagement.production.masterdata.fiber.infra.repository;

import com.fabricmanagement.production.masterdata.fiber.domain.reference.FiberIsoCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FiberIsoCodeRepository extends JpaRepository<FiberIsoCode, UUID> {
    
    List<FiberIsoCode> findByIsActiveTrue();
    
    Optional<FiberIsoCode> findByIsoCode(String isoCode);
}

