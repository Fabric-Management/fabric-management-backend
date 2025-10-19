package com.fabricmanagement.fiber.api.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class FiberValidationResponse {
    
    private Boolean valid;
    private List<String> activeFibers;
    private List<String> inactiveFibers;
    private List<String> notFoundFibers;
    private String message;
}

