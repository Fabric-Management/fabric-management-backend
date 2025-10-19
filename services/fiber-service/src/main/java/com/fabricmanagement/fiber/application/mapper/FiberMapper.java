package com.fabricmanagement.fiber.application.mapper;

import com.fabricmanagement.fiber.api.dto.request.*;
import com.fabricmanagement.fiber.api.dto.response.FiberResponse;
import com.fabricmanagement.fiber.api.dto.response.FiberSummaryResponse;
import com.fabricmanagement.fiber.domain.aggregate.Fiber;
import com.fabricmanagement.fiber.domain.valueobject.*;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class FiberMapper {
    
    private static final UUID GLOBAL_TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");
    
    public Fiber fromCreateRequest(CreateFiberRequest request) {
        // ⚠️ CRITICAL: DON'T set .id(UUID.randomUUID()) manually!
        // BaseEntity has @GeneratedValue(strategy = GenerationType.UUID)
        // Hibernate will auto-generate UUID on persist
        // Setting ID manually caused 3-4 days of debugging in the past!
        return Fiber.builder()
                .tenantId(GLOBAL_TENANT_ID)
                .code(request.getCode())
                .name(request.getName())
                .category(FiberCategory.valueOf(request.getCategory()))
                .compositionType(CompositionType.PURE)
                .originType(OriginType.valueOf(request.getOriginType()))
                .sustainabilityType(SustainabilityType.valueOf(request.getSustainabilityType()))
                .status(FiberStatus.ACTIVE)
                .isDefault(false)
                .reusable(request.getReusable() != null ? request.getReusable() : true)
                .property(mapProperty(request.getProperty()))
                .components(null)
                .createdBy("SYSTEM")
                .build();
    }
    
    public Fiber fromCreateBlendRequest(CreateBlendFiberRequest request) {
        List<FiberComponent> components = request.getComponents().stream()
                .map(this::mapComponent)
                .collect(Collectors.toList());
        
        // ⚠️ CRITICAL: DON'T set .id(UUID.randomUUID()) manually!
        // BaseEntity has @GeneratedValue(strategy = GenerationType.UUID)
        // Hibernate will auto-generate UUID on persist
        return Fiber.builder()
                .tenantId(GLOBAL_TENANT_ID)
                .code(request.getCode())
                .name(request.getName())
                .category(FiberCategory.BLEND)
                .compositionType(CompositionType.BLEND)
                .originType(OriginType.valueOf(request.getOriginType()))
                .sustainabilityType(SustainabilityType.CONVENTIONAL)
                .status(FiberStatus.ACTIVE)
                .isDefault(false)
                .reusable(request.getReusable() != null ? request.getReusable() : true)
                .property(null)
                .components(components)
                .createdBy("SYSTEM")
                .build();
    }
    
    public FiberResponse toResponse(Fiber fiber) {
        return FiberResponse.builder()
                .id(fiber.getId().toString())
                .code(fiber.getCode())
                .name(fiber.getName())
                .category(fiber.getCategory().name())
                .compositionType(fiber.getCompositionType().name())
                .originType(fiber.getOriginType().name())
                .sustainabilityType(fiber.getSustainabilityType().name())
                .status(fiber.getStatus().name())
                .isDefault(fiber.getIsDefault())
                .reusable(fiber.getReusable())
                .property(mapPropertyToDto(fiber.getProperty()))
                .components(mapComponentsToDtos(fiber.getComponents()))
                .createdAt(fiber.getCreatedAt())
                .createdBy(fiber.getCreatedBy())
                .updatedAt(fiber.getUpdatedAt())
                .version(fiber.getVersion())
                .build();
    }
    
    public FiberSummaryResponse toSummaryResponse(Fiber fiber) {
        return FiberSummaryResponse.builder()
                .id(fiber.getId().toString())
                .code(fiber.getCode())
                .name(fiber.getName())
                .category(fiber.getCategory().name())
                .compositionType(fiber.getCompositionType().name())
                .status(fiber.getStatus().name())
                .isDefault(fiber.getIsDefault())
                .build();
    }
    
    private FiberProperty mapProperty(FiberPropertyDto dto) {
        if (dto == null) {
            return null;
        }
        return FiberProperty.builder()
                .stapleLength(dto.getStapleLength())
                .fineness(dto.getFineness())
                .tenacity(dto.getTenacity())
                .moistureRegain(dto.getMoistureRegain())
                .color(dto.getColor())
                .build();
    }
    
    private FiberPropertyDto mapPropertyToDto(FiberProperty property) {
        if (property == null) {
            return null;
        }
        FiberPropertyDto dto = new FiberPropertyDto();
        dto.setStapleLength(property.getStapleLength());
        dto.setFineness(property.getFineness());
        dto.setTenacity(property.getTenacity());
        dto.setMoistureRegain(property.getMoistureRegain());
        dto.setColor(property.getColor());
        return dto;
    }
    
    private FiberComponent mapComponent(FiberComponentDto dto) {
        return FiberComponent.builder()
                .fiberCode(dto.getFiberCode())
                .percentage(dto.getPercentage())
                .sustainabilityType(dto.getSustainabilityType() != null 
                        ? SustainabilityType.valueOf(dto.getSustainabilityType())
                        : SustainabilityType.CONVENTIONAL)
                .build();
    }
    
    private List<FiberComponentDto> mapComponentsToDtos(List<FiberComponent> components) {
        if (components == null || components.isEmpty()) {
            return new ArrayList<>();
        }
        return components.stream()
                .map(this::mapComponentToDto)
                .collect(Collectors.toList());
    }
    
    private FiberComponentDto mapComponentToDto(FiberComponent component) {
        FiberComponentDto dto = new FiberComponentDto();
        dto.setFiberCode(component.getFiberCode());
        dto.setPercentage(component.getPercentage());
        dto.setSustainabilityType(component.getSustainabilityType() != null 
                ? component.getSustainabilityType().name() 
                : null);
        return dto;
    }
}
