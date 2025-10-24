package com.fabricmanagement.fiber.fixtures;

import com.fabricmanagement.fiber.api.dto.request.CreateBlendFiberRequest;
import com.fabricmanagement.fiber.api.dto.request.CreateFiberRequest;
import com.fabricmanagement.fiber.api.dto.request.FiberComponentDto;
import com.fabricmanagement.fiber.api.dto.request.UpdateFiberPropertyRequest;
import com.fabricmanagement.fiber.domain.aggregate.Fiber;
import com.fabricmanagement.fiber.domain.valueobject.*;
import static com.fabricmanagement.fiber.support.TestSupport.*;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

/**
 * Test Data Builders for Fiber Domain
 *
 * Pattern: Test Data Builder (Google/Netflix style)
 * - Readable test data creation
 * - Sensible defaults
 * - Easy customization
 * - No magic values
 *
 * Usage:
 *   Fiber fiber = createPureFiber("CO", "Cotton");
 *   CreateFiberRequest request = createPureFiberRequest("PES", "Polyester");
 */
public class FiberFixtures {

    // ═════════════════════════════════════════════════════
    // PURE FIBER BUILDERS (Domain Entity)
    // ═════════════════════════════════════════════════════

    public static Fiber createPureFiber(String code, String name) {
        // ⚠️ CRITICAL: DON'T set .id() or .version() manually!
        // BaseEntity has @GeneratedValue(UUID) and @Version
        // Hibernate will auto-generate on persist
        // Setting manually caused 3-4 days debugging!
        return Fiber.builder()
                .tenantId(GLOBAL_TENANT_ID)
                .code(code)
                .name(name)
                .category(FiberCategory.NATURAL)
                .compositionType(CompositionType.PURE)
                .originType(OriginType.UNKNOWN)
                .sustainabilityType(SustainabilityType.CONVENTIONAL)
                .status(FiberStatus.ACTIVE)
                .isDefault(false)
                .reusable(true)
                .property(null)
                .components(null)
                .createdBy(TEST_USER)
                .deleted(false)
                .build();
    }

    public static Fiber createDefaultFiber(String code) {
        Fiber fiber = createPureFiber(code, code + " Default");
        fiber.setIsDefault(true);
        fiber.setCreatedBy(SYSTEM_USER);
        return fiber;
    }

    public static Fiber createFiber(String code, String name, FiberCategory category) {
        Fiber fiber = createPureFiber(code, name);
        fiber.setCategory(category);
        return fiber;
    }

    public static Fiber createSyntheticFiber(String code, String name) {
        Fiber fiber = createPureFiber(code, name);
        fiber.setCategory(FiberCategory.SYNTHETIC);
        return fiber;
    }

    public static Fiber createFiberWithProperty(String code, FiberProperty property) {
        Fiber fiber = createPureFiber(code, code);
        fiber.setProperty(property);
        return fiber;
    }

    // ═════════════════════════════════════════════════════
    // BLEND FIBER BUILDERS (Domain Entity)
    // ═════════════════════════════════════════════════════

    public static Fiber createBlendFiber(String code, String name, List<FiberComponent> components) {
        // ⚠️ CRITICAL: DON'T set .id() or .version() manually!
        // Hibernate will auto-generate on persist
        return Fiber.builder()
                .tenantId(GLOBAL_TENANT_ID)
                .code(code)
                .name(name)
                .category(FiberCategory.BLEND)
                .compositionType(CompositionType.BLEND)
                .components(components)
                .originType(OriginType.UNKNOWN)
                .sustainabilityType(SustainabilityType.CONVENTIONAL)
                .status(FiberStatus.ACTIVE)
                .isDefault(false)
                .reusable(true)
                .property(null)
                .createdBy(TEST_USER)
                .deleted(false)
                .build();
    }

    public static Fiber createCottonPolyesterBlend() {
        return createBlendFiber(
                "BLD-001",
                "Cotton/Polyester 60/40",
                Arrays.asList(
                        component(CODE_COTTON, PCT_60.doubleValue()),
                        component(CODE_POLYESTER, PCT_40.doubleValue())
                )
        );
    }

    // ═════════════════════════════════════════════════════
    // COMPONENT BUILDERS (Value Object)
    // ═════════════════════════════════════════════════════

    public static FiberComponent component(String fiberCode, double percentage) {
        return FiberComponent.builder()
                .fiberCode(fiberCode)
                .percentage(BigDecimal.valueOf(percentage))
                .sustainabilityType(SustainabilityType.CONVENTIONAL)
                .build();
    }

    public static FiberComponent component(String fiberCode, double percentage, SustainabilityType sustainability) {
        return FiberComponent.builder()
                .fiberCode(fiberCode)
                .percentage(BigDecimal.valueOf(percentage))
                .sustainabilityType(sustainability)
                .build();
    }

    public static List<FiberComponent> createComponentList(FiberComponent... components) {
        return Arrays.asList(components);
    }

    public static List<FiberComponent> createValidBlendComponents() {
        return Arrays.asList(
                component(CODE_COTTON, PCT_60.doubleValue()),
                component(CODE_POLYESTER, PCT_40.doubleValue())
        );
    }

    public static List<FiberComponent> createInvalidBlendComponents() {
        return Arrays.asList(
                component(CODE_COTTON, PCT_60.doubleValue()),
                component(CODE_POLYESTER, 30.0)  // Total = 90% ❌
        );
    }

    // ═════════════════════════════════════════════════════
    // PROPERTY BUILDERS (Value Object)
    // ═════════════════════════════════════════════════════

    public static FiberProperty createCottonProperty() {
        return FiberProperty.builder()
                .stapleLength(BigDecimal.valueOf(32.0))
                .fineness(BigDecimal.valueOf(1.8))
                .tenacity(BigDecimal.valueOf(2.8))
                .moistureRegain(BigDecimal.valueOf(7.5))
                .color("RawWhite")
                .build();
    }

    public static FiberProperty createPolyesterProperty() {
        return FiberProperty.builder()
                .stapleLength(BigDecimal.valueOf(51.0))
                .fineness(BigDecimal.valueOf(3.3))
                .tenacity(BigDecimal.valueOf(5.0))
                .moistureRegain(BigDecimal.valueOf(0.4))
                .color("RawWhite")
                .build();
    }

    // ═════════════════════════════════════════════════════
    // REQUEST DTO BUILDERS (API Layer)
    // ═════════════════════════════════════════════════════

    public static CreateFiberRequest createPureFiberRequest(String code, String name) {
        CreateFiberRequest request = new CreateFiberRequest();
        request.setCode(code);
        request.setName(name);
        request.setCategory("NATURAL");
        request.setOriginType("UNKNOWN");
        request.setSustainabilityType("CONVENTIONAL");
        request.setReusable(true);
        return request;
    }

    public static CreateFiberRequest createSyntheticFiberRequest(String code, String name) {
        CreateFiberRequest request = createPureFiberRequest(code, name);
        request.setCategory("SYNTHETIC");
        return request;
    }

    public static CreateBlendFiberRequest createBlendFiberRequest(String code, String name) {
        CreateBlendFiberRequest request = new CreateBlendFiberRequest();
        request.setCode(code);
        request.setName(name);
        request.setComponents(Arrays.asList(
                createComponentDto(CODE_COTTON, PCT_60.doubleValue()),
                createComponentDto(CODE_POLYESTER, PCT_40.doubleValue())
        ));
        request.setOriginType("UNKNOWN");
        request.setReusable(true);
        return request;
    }

    public static FiberComponentDto createComponentDto(String fiberCode, double percentage) {
        FiberComponentDto dto = new FiberComponentDto();
        dto.setFiberCode(fiberCode);
        dto.setPercentage(BigDecimal.valueOf(percentage));
        dto.setSustainabilityType("CONVENTIONAL");
        return dto;
    }

    public static UpdateFiberPropertyRequest createUpdatePropertyRequest() {
        UpdateFiberPropertyRequest request = new UpdateFiberPropertyRequest();
        request.setStapleLength(BigDecimal.valueOf(35.0));
        request.setFineness(BigDecimal.valueOf(1.9));
        request.setTenacity(BigDecimal.valueOf(2.9));
        request.setMoistureRegain(BigDecimal.valueOf(8.0));
        request.setColor("Bleached");
        request.setSustainabilityType("ORGANIC");
        return request;
    }

    // ═════════════════════════════════════════════════════
    // COMMON TEST FIBERS (Predefined)
    // ═════════════════════════════════════════════════════

    public static Fiber cotton() {
        return createDefaultFiber(CODE_COTTON);
    }

    public static Fiber polyester() {
        return createDefaultFiber(CODE_POLYESTER);
    }

    public static Fiber wool() {
        return createDefaultFiber("WO");
    }

    public static Fiber silk() {
        return createDefaultFiber("SI");
    }

    public static Fiber linen() {
        return createDefaultFiber("LI");
    }

    // ═════════════════════════════════════════════════════
    // HELPER METHODS
    // ═════════════════════════════════════════════════════

    public static List<Fiber> createDefaultFiberSet() {
        return Arrays.asList(
                cotton(),
                polyester(),
                wool(),
                silk(),
                linen(),
                createDefaultFiber("NY"),
                createDefaultFiber("VI"),
                createDefaultFiber("AC"),
                createDefaultFiber("MD")
        );
    }
}

