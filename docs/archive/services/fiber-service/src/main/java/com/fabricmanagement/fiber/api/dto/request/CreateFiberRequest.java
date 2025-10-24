package com.fabricmanagement.fiber.api.dto.request;

import lombok.Data;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

@Data
public class CreateFiberRequest {
    
    @NotBlank(message = "Fiber code is required")
    @Size(min = 2, max = 20, message = "Code must be between 2 and 20 characters")
    @Pattern(regexp = "^[A-Z0-9-]+$", message = "Code must be uppercase alphanumeric with hyphens")
    private String code;
    
    @NotBlank(message = "Fiber name is required")
    @Size(min = 2, max = 100, message = "Name must be between 2 and 100 characters")
    private String name;
    
    @NotNull(message = "Category is required")
    private String category;
    
    @NotNull(message = "Origin type is required")
    private String originType;
    
    @NotNull(message = "Sustainability type is required")
    private String sustainabilityType;
    
    @Valid
    private FiberPropertyDto property;
    
    private Boolean reusable = true;
}

