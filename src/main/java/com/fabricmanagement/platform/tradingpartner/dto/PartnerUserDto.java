package com.fabricmanagement.platform.tradingpartner.dto;

import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO representing a partner portal user as seen by tenant admins.
 *
 * <p>Status:
 *
 * <ul>
 *   <li>{@code ACTIVE} — has set a password and logged in at least once
 *   <li>{@code INVITED} — invitation sent, setup not yet completed
 *   <li>{@code SUSPENDED} — access temporarily disabled
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PartnerUserDto {

  private UUID userId;
  private String uid;
  private String displayName;
  private String email;
  private String partnerRoleCode;
  private String partnerRoleName;
  private String status;
  private Instant lastLoginAt;
  private Instant invitedAt;
  private UUID partnerId;
  private UUID organizationId;
}
