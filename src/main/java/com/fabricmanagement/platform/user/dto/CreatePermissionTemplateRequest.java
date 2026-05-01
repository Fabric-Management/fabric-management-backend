package com.fabricmanagement.platform.user.dto;

import com.fabricmanagement.platform.user.domain.DataScope;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreatePermissionTemplateRequest {

  @NotBlank private String roleCode;

  private String departmentCode; // Null means global for that role

  @NotBlank private String resource;

  @NotBlank private String action;

  @NotNull private DataScope dataScope;
}
