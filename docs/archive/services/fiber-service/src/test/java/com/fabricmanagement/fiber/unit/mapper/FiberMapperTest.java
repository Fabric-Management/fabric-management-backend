package com.fabricmanagement.fiber.unit.mapper;

import com.fabricmanagement.fiber.api.dto.request.CreateBlendFiberRequest;
import com.fabricmanagement.fiber.api.dto.request.CreateFiberRequest;
import com.fabricmanagement.fiber.api.dto.request.FiberComponentDto;
import com.fabricmanagement.fiber.api.dto.request.FiberPropertyDto;
import com.fabricmanagement.fiber.api.dto.response.FiberResponse;
import com.fabricmanagement.fiber.api.dto.response.FiberSummaryResponse;
import com.fabricmanagement.fiber.application.mapper.FiberMapper;
import com.fabricmanagement.fiber.domain.aggregate.Fiber;
import com.fabricmanagement.fiber.domain.valueobject.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("FiberMapper - Unit Tests")
class FiberMapperTest {

    private FiberMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new FiberMapper();
    }

    @Test
    @DisplayName("Should map CreateFiberRequest to Fiber entity")
    void shouldMapCreateRequestToEntity() {
        CreateFiberRequest request = new CreateFiberRequest();
        request.setCode("CO");
        request.setName("Cotton");
        request.setCategory("NATURAL");
        request.setOriginType("PLANT");
        request.setSustainabilityType("ORGANIC");
        request.setReusable(true);
        
        FiberPropertyDto propertyDto = new FiberPropertyDto();
        propertyDto.setStapleLength(BigDecimal.valueOf(30));
        propertyDto.setFineness(BigDecimal.valueOf(1.5));
        request.setProperty(propertyDto);

        Fiber fiber = mapper.fromCreateRequest(request);

        assertThat(fiber.getCode()).isEqualTo("CO");
        assertThat(fiber.getName()).isEqualTo("Cotton");
        assertThat(fiber.getCategory()).isEqualTo(FiberCategory.NATURAL);
        assertThat(fiber.getOriginType()).isEqualTo(OriginType.PLANT);
        assertThat(fiber.getSustainabilityType()).isEqualTo(SustainabilityType.ORGANIC);
        assertThat(fiber.getStatus()).isEqualTo(FiberStatus.ACTIVE);
        assertThat(fiber.getCompositionType()).isEqualTo(CompositionType.PURE);
    }

    @Test
    @DisplayName("Should map CreateBlendFiberRequest to Fiber entity")
    void shouldMapCreateBlendRequestToEntity() {
        CreateBlendFiberRequest request = new CreateBlendFiberRequest();
        request.setCode("BLD-001");
        request.setName("Cotton/Polyester");
        request.setOriginType("UNKNOWN");
        request.setReusable(true);
        
        FiberComponentDto comp1 = new FiberComponentDto();
        comp1.setFiberCode("CO");
        comp1.setPercentage(BigDecimal.valueOf(60));
        comp1.setSustainabilityType("ORGANIC");
        
        FiberComponentDto comp2 = new FiberComponentDto();
        comp2.setFiberCode("PES");
        comp2.setPercentage(BigDecimal.valueOf(40));
        
        request.setComponents(Arrays.asList(comp1, comp2));

        Fiber fiber = mapper.fromCreateBlendRequest(request);

        assertThat(fiber.getCode()).isEqualTo("BLD-001");
        assertThat(fiber.getCompositionType()).isEqualTo(CompositionType.BLEND);
        assertThat(fiber.getComponents()).hasSize(2);
        assertThat(fiber.getComponents().get(0).getFiberCode()).isEqualTo("CO");
        assertThat(fiber.getComponents().get(0).getPercentage()).isEqualTo(BigDecimal.valueOf(60));
    }

    @Test
    @DisplayName("Should map Fiber entity to FiberResponse")
    void shouldMapEntityToResponse() {
        Fiber fiber = Fiber.builder()
                .id(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .code("CO")
                .name("Cotton")
                .category(FiberCategory.NATURAL)
                .compositionType(CompositionType.PURE)
                .originType(OriginType.PLANT)
                .sustainabilityType(SustainabilityType.ORGANIC)
                .status(FiberStatus.ACTIVE)
                .isDefault(true)
                .reusable(true)
                .components(Collections.emptyList())
                .build();

        FiberResponse response = mapper.toResponse(fiber);

        assertThat(response.getCode()).isEqualTo("CO");
        assertThat(response.getName()).isEqualTo("Cotton");
        assertThat(response.getCategory()).isEqualTo("NATURAL");
        assertThat(response.getIsDefault()).isTrue();
    }

    @Test
    @DisplayName("Should map Fiber entity to FiberSummaryResponse")
    void shouldMapEntityToSummaryResponse() {
        Fiber fiber = Fiber.builder()
                .id(UUID.randomUUID())
                .code("CO")
                .name("Cotton")
                .category(FiberCategory.NATURAL)
                .compositionType(CompositionType.PURE)
                .status(FiberStatus.ACTIVE)
                .isDefault(true)
                .build();

        FiberSummaryResponse response = mapper.toSummaryResponse(fiber);

        assertThat(response.getCode()).isEqualTo("CO");
        assertThat(response.getName()).isEqualTo("Cotton");
        assertThat(response.getCategory()).isEqualTo("NATURAL");
        assertThat(response.getCompositionType()).isEqualTo("PURE");
        assertThat(response.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("Should handle null components in blend fiber")
    void shouldHandleNullComponents() {
        Fiber fiber = Fiber.builder()
                .id(UUID.randomUUID())
                .code("BLD-001")
                .name("Blend")
                .category(FiberCategory.BLEND)
                .compositionType(CompositionType.BLEND)
                .originType(OriginType.UNKNOWN)
                .sustainabilityType(SustainabilityType.CONVENTIONAL)
                .status(FiberStatus.ACTIVE)
                .isDefault(false)
                .reusable(true)
                .components(null)
                .build();

        FiberResponse response = mapper.toResponse(fiber);

        assertThat(response.getComponents()).isEmpty();
    }

    @Test
    @DisplayName("Should handle empty components in blend fiber")
    void shouldHandleEmptyComponents() {
        Fiber fiber = Fiber.builder()
                .id(UUID.randomUUID())
                .code("BLD-001")
                .name("Blend")
                .category(FiberCategory.BLEND)
                .compositionType(CompositionType.BLEND)
                .originType(OriginType.UNKNOWN)
                .sustainabilityType(SustainabilityType.CONVENTIONAL)
                .status(FiberStatus.ACTIVE)
                .isDefault(false)
                .reusable(true)
                .components(Collections.emptyList())
                .build();

        FiberResponse response = mapper.toResponse(fiber);

        assertThat(response.getComponents()).isEmpty();
    }
}

