package com.fabricmanagement.platform.user.dto;

import com.fabricmanagement.platform.user.domain.DataScope;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdatePermissionTemplateRequest {

  @NotNull private DataScope dataScope;

  @NotNull private Boolean isActive;
}
