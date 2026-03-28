package com.fabricmanagement.platform.user.dto;

import com.fabricmanagement.platform.user.domain.RoleScope;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoleDto {

  private UUID id;
  private UUID tenantId;
  private String uid;
  private String roleName;
  private String roleCode;
  private String description;
  private RoleScope roleScope;
  private Boolean isActive;
}
