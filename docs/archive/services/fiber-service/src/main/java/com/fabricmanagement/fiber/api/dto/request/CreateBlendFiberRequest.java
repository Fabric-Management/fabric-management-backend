package com.fabricmanagement.fiber.api.dto.request;

import lombok.Data;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

@Data
public class CreateBlendFiberRequest {
    
    @NotBlank(message = "Blend fiber code is required")
    @Pattern(regexp = "^BLD-[0-9]{3,}$", message = "Blend code must start with BLD-")
    private String code;
    
    @NotBlank(message = "Blend fiber name is required")
    private String name;
    
    @NotNull(message = "Components are required")
    @Size(min = 2, message = "Blend must have at least 2 components")
    @Valid
    private List<FiberComponentDto> components;
    
    @NotNull(message = "Origin type is required")
    private String originType;
    
    private Boolean reusable = true;
}

