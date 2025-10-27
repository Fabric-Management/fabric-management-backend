package com.fabricmanagement.production.masterdata.fiber.infra.repository;

import com.fabricmanagement.production.masterdata.fiber.domain.reference.FiberIsoCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Fiber ISO Code reference data.
 */
@Repository
public interface FiberIsoCodeRepository extends JpaRepository<FiberIsoCode, UUID> {

    Optional<FiberIsoCode> findByIsoCode(String isoCode);

    @Query("SELECT f FROM FiberIsoCode f WHERE f.isActive = true ORDER BY f.displayOrder ASC")
    List<FiberIsoCode> findAllActive();

    boolean existsByIsoCode(String isoCode);
}

