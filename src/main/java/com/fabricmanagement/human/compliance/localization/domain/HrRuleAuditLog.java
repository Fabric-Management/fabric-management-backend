package com.fabricmanagement.human.compliance.localization.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "human_hr_rule_audit_log", schema = "human")
@Getter
@Setter
@NoArgsConstructor
public class HrRuleAuditLog extends BaseEntity {

  @Column(name = "policy_pack_id", nullable = false)
  private UUID policyPackId;

  @Column(name = "pack_code", nullable = false, length = 100)
  private String packCode;

  @Column(name = "country_code", nullable = false, length = 8)
  private String countryCode;

  @Column(name = "pack_version", nullable = false)
  private Integer packVersion;

  @Enumerated(EnumType.STRING)
  @Column(name = "action", nullable = false, length = 30)
  private HrPolicyPackAction action;

  @Column(name = "actor_id")
  private UUID actorId;

  @Column(name = "payload_checksum", length = 128)
  private String payloadChecksum;

  @Column(name = "diff_snapshot", columnDefinition = "jsonb")
  private String diffSnapshot;

  @Column(name = "occurred_at", nullable = false)
  private Instant occurredAt;

  @Override
  protected String getModuleCode() {
    return "HRA";
  }
}
