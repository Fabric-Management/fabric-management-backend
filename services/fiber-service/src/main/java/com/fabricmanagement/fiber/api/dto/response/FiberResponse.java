package com.fabricmanagement.fiber.api.dto.response;

import com.fabricmanagement.fiber.api.dto.request.FiberComponentDto;
import com.fabricmanagement.fiber.api.dto.request.FiberPropertyDto;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class FiberResponse {
    
    private String id;
    private String code;
    private String name;
    private String category;
    private String compositionType;
    private String originType;
    private String sustainabilityType;
    private String status;
    
    private Boolean isDefault;
    private Boolean reusable;
    
    private FiberPropertyDto property;
    private List<FiberComponentDto> components;
    
    private LocalDateTime createdAt;
    private String createdBy;
    private LocalDateTime updatedAt;
    private Long version;
}

