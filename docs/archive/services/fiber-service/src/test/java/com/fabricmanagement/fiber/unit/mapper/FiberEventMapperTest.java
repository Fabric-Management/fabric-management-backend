package com.fabricmanagement.fiber.unit.mapper;

import com.fabricmanagement.fiber.application.mapper.FiberEventMapper;
import com.fabricmanagement.fiber.domain.aggregate.Fiber;
import com.fabricmanagement.fiber.domain.event.FiberDeactivatedEvent;
import com.fabricmanagement.fiber.domain.event.FiberDefinedEvent;
import com.fabricmanagement.fiber.domain.event.FiberUpdatedEvent;
import com.fabricmanagement.fiber.domain.valueobject.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("FiberEventMapper - Unit Tests")
class FiberEventMapperTest {

    private FiberEventMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new FiberEventMapper();
    }

    @Test
    @DisplayName("Should map Fiber to FiberDefinedEvent")
    void shouldMapToDefinedEvent() {
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
                .isDefault(false)
                .reusable(true)
                .components(Collections.emptyList())
                .build();

        FiberDefinedEvent event = mapper.toDefinedEvent(fiber);

        assertThat(event).isNotNull();
        assertThat(event.getFiberId()).isEqualTo(fiber.getId());
        assertThat(event.getCode()).isEqualTo("CO");
        assertThat(event.getName()).isEqualTo("Cotton");
        assertThat(event.getCategory()).isEqualTo("NATURAL");
        assertThat(event.getOccurredAt()).isNotNull();
    }

    @Test
    @DisplayName("Should map Fiber to FiberUpdatedEvent")
    void shouldMapToUpdatedEvent() {
        Fiber fiber = Fiber.builder()
                .id(UUID.randomUUID())
                .code("CO")
                .name("Cotton Updated")
                .category(FiberCategory.NATURAL)
                .compositionType(CompositionType.PURE)
                .originType(OriginType.PLANT)
                .sustainabilityType(SustainabilityType.ORGANIC)
                .status(FiberStatus.ACTIVE)
                .isDefault(false)
                .reusable(true)
                .build();

        FiberUpdatedEvent event = mapper.toUpdatedEvent(fiber);

        assertThat(event).isNotNull();
        assertThat(event.getFiberId()).isEqualTo(fiber.getId());
        assertThat(event.getCode()).isEqualTo("CO");
        assertThat(event.getOccurredAt()).isNotNull();
    }

    @Test
    @DisplayName("Should map Fiber to FiberDeactivatedEvent")
    void shouldMapToDeactivatedEvent() {
        Fiber fiber = Fiber.builder()
                .id(UUID.randomUUID())
                .code("CO")
                .name("Cotton")
                .category(FiberCategory.NATURAL)
                .compositionType(CompositionType.PURE)
                .originType(OriginType.PLANT)
                .sustainabilityType(SustainabilityType.ORGANIC)
                .status(FiberStatus.INACTIVE)
                .isDefault(false)
                .reusable(true)
                .build();

        FiberDeactivatedEvent event = mapper.toDeactivatedEvent(fiber);

        assertThat(event).isNotNull();
        assertThat(event.getFiberId()).isEqualTo(fiber.getId());
        assertThat(event.getCode()).isEqualTo("CO");
        assertThat(event.getOccurredAt()).isNotNull();
    }
}

