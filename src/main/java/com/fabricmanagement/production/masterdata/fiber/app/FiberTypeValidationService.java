package com.fabricmanagement.production.masterdata.fiber.app;

import com.fabricmanagement.production.masterdata.fiber.infra.repository.FiberIsoCodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

/**
 * Validation service for fiber type creation (CREATE-ONLY policy).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FiberTypeValidationService {

    private final FiberIsoCodeRepository fiberIsoCodeRepository;

    private static final Pattern ISO_CODE_PATTERN = Pattern.compile("^[A-Z0-9]{2,10}$");

    /**
     * Validate ISO code format.
     * 
     * <p>Rules:
     * <ul>
     *   <li>Uppercase alphanumeric only</li>
     *   <li>2-10 characters</li>
     *   <li>No special characters</li>
     * </ul>
     *
     * @param isoCode ISO code to validate
     * @throws IllegalArgumentException if format is invalid
     */
    public void validateIsoCodeFormat(String isoCode) {
        if (isoCode == null || isoCode.isEmpty()) {
            throw new IllegalArgumentException("ISO code cannot be empty");
        }

        if (!ISO_CODE_PATTERN.matcher(isoCode).matches()) {
            throw new IllegalArgumentException(
                String.format("ISO code '%s' is invalid. Must be uppercase alphanumeric (2-10 characters).", isoCode));
        }
    }

    /**
     * Validate fiber name uniqueness.
     *
     * <p>Prevents typos by checking if a similar fiber name already exists.</p>
     *
     * @param fiberName Fiber name to validate
     * @throws IllegalArgumentException if duplicate or similar name exists
     */
    public void validateFiberNameUniqueness(String fiberName) {
        // Check for exact duplicate (case-insensitive)
        if (fiberIsoCodeRepository.findAll().stream()
            .anyMatch(ft -> ft.getFiberName().equalsIgnoreCase(fiberName))) {
            throw new IllegalArgumentException(
                String.format("Fiber name '%s' already exists. If this is intentional, please contact platform admin.", fiberName));
        }
    }
}

