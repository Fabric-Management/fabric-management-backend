package com.fabricmanagement.platform.user.dto;

import com.fabricmanagement.platform.tenant.domain.TenantStatus;
import com.fabricmanagement.platform.tenant.dto.TenantDto;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CurrentTenantContextDto {

  private boolean demoMode;
  private TenantStatus status;
  private Instant trialEndsAt;

  public static CurrentTenantContextDto from(TenantDto tenant) {
    return CurrentTenantContextDto.builder()
        .demoMode(tenant.isDemoMode())
        .status(tenant.getStatus())
        .trialEndsAt(tenant.getTrialEndsAt())
        .build();
  }
}
