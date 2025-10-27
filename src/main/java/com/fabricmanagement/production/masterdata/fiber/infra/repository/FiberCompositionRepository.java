package com.fabricmanagement.production.masterdata.fiber.infra.repository;

import com.fabricmanagement.production.masterdata.fiber.domain.FiberComposition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface FiberCompositionRepository extends JpaRepository<FiberComposition, UUID> {

    /**
     * Find all compositions for a blended fiber.
     */
    List<FiberComposition> findByBlendedFiberIdAndIsActiveTrue(UUID blendedFiberId);

    /**
     * Find all compositions that use a specific base fiber.
     */
    List<FiberComposition> findByBaseFiberIdAndIsActiveTrue(UUID baseFiberId);

    /**
     * Get total percentage sum for a blended fiber.
     */
    @Query("SELECT COALESCE(SUM(fc.percentage), 0) FROM FiberComposition fc WHERE fc.blendedFiberId = :blendedFiberId AND fc.isActive = true")
    Double getTotalPercentage(UUID blendedFiberId);

    /**
     * Delete all compositions for a fiber (cascade delete).
     */
    void deleteByBlendedFiberId(UUID blendedFiberId);

    /**
     * Count compositions for a blended fiber.
     */
    long countByBlendedFiberIdAndIsActiveTrue(UUID blendedFiberId);
}

