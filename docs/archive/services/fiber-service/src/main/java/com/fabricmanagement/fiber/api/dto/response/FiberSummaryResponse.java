package com.fabricmanagement.fiber.api.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FiberSummaryResponse {
    
    private String id;
    private String code;
    private String name;
    private String category;
    private String compositionType;
    private String status;
    private Boolean isDefault;
}

