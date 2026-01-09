package com.fabricmanagement.human.compliance.localization.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "human_hr_policy_binding", schema = "human")
@Getter
@Setter
@NoArgsConstructor
public class HrPolicyBinding extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "policy_pack_id", nullable = false)
  private HrPolicyPack policyPack;

  @Column(name = "policy_interface", nullable = false, length = 150)
  private String policyInterface;

  @Column(name = "strategy_bean", nullable = false, length = 150)
  private String strategyBean;

  @Column(name = "config_reference", columnDefinition = "jsonb")
  private String configReference;

  @Override
  protected String getModuleCode() {
    return "HRB";
  }
}
