package com.fabricmanagement.platform.audit.dto;

import com.fabricmanagement.platform.audit.domain.AuditSeverity;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogDto {

  private UUID id;
  private UUID tenantId;
  private String uid;
  private UUID userId;
  private String userUid;
  private String action;
  private String resource;
  private String resourceId;
  private String description;
  private String oldValue;
  private String newValue;
  private String ipAddress;
  private String userAgent;
  private AuditSeverity severity;
  private Instant timestamp;
}
