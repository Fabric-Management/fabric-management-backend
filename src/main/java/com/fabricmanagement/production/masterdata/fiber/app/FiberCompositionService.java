package com.fabricmanagement.production.masterdata.fiber.app;

import com.fabricmanagement.production.masterdata.fiber.domain.FiberComposition;
import com.fabricmanagement.production.masterdata.fiber.infra.repository.FiberCompositionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Fiber Composition Service - Handles blended fiber composition logic.
 *
 * <p>Validates that blended fiber compositions always sum to exactly 100%.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FiberCompositionService {

    private final FiberCompositionRepository compositionRepository;

    /**
     * Validate that compositions sum to 100%.
     *
     * @param blendedFiberId Blended fiber ID
     * @return true if total is 100%
     * @throws IllegalArgumentException if total is not 100%
     */
    @Transactional(readOnly = true)
    public boolean validateCompositionPercentage(UUID blendedFiberId) {
        Double totalPercentage = compositionRepository.getTotalPercentage(blendedFiberId);
        
        if (totalPercentage == null || Math.abs(totalPercentage - 100.0) > 0.01) {
            throw new IllegalArgumentException(
                String.format("Fiber composition must sum to exactly 100%%, got: %.2f%%", totalPercentage));
        }
        
        return true;
    }

    /**
     * Set composition for a blended fiber.
     *
     * @param blendedFiberId Blended fiber ID
     * @param composition Map of baseFiberId → percentage
     * @throws IllegalArgumentException if total is not 100%
     */
    @Transactional
    public void setComposition(UUID blendedFiberId, Map<UUID, BigDecimal> composition) {
        log.info("Setting composition for fiber: {}", blendedFiberId);

        // Validation is handled by FiberValidationService in FiberService
        // No duplicate validation here - just save the composition

        // Delete existing compositions
        compositionRepository.deleteByBlendedFiberId(blendedFiberId);

        // Create new compositions
        List<FiberComposition> compositions = composition.entrySet().stream()
            .map(entry -> FiberComposition.builder()
                .blendedFiberId(blendedFiberId)
                .baseFiberId(entry.getKey())
                .percentage(entry.getValue())
                .build())
            .toList();

        compositionRepository.saveAll(compositions);

        log.info("✅ Composition set for fiber: {}", blendedFiberId);
    }

    /**
     * Get composition for a blended fiber.
     *
     * @param blendedFiberId Blended fiber ID
     * @return Map of baseFiberId → percentage
     */
    @Transactional(readOnly = true)
    public Map<UUID, BigDecimal> getComposition(UUID blendedFiberId) {
        List<FiberComposition> compositions = compositionRepository.findByBlendedFiberIdAndIsActiveTrue(blendedFiberId);
        
        return compositions.stream()
            .collect(Collectors.toMap(
                FiberComposition::getBaseFiberId,
                FiberComposition::getPercentage
            ));
    }
}

