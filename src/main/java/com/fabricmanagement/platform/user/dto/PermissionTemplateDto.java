package com.fabricmanagement.platform.user.dto;

import com.fabricmanagement.platform.user.domain.DataScope;
import com.fabricmanagement.platform.user.domain.PermissionTemplate;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PermissionTemplateDto {
  private UUID id;
  private String roleCode;
  private String departmentCode;
  private String resource;
  private String action;
  private DataScope dataScope;
  private boolean isSystemDefault;
  private boolean isActive;

  public static PermissionTemplateDto from(PermissionTemplate template) {
    if (template == null) return null;
    return PermissionTemplateDto.builder()
        .id(template.getId())
        .roleCode(template.getRoleCode())
        .departmentCode(template.getDepartmentCode())
        .resource(template.getResource())
        .action(template.getAction())
        .dataScope(template.getDataScope())
        .isSystemDefault(template.getTenantId() == null)
        .isActive(template.getIsActive())
        .build();
  }
}
