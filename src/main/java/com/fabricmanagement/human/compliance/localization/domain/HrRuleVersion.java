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
@Table(name = "human_hr_rule_version", schema = "human")
@Getter
@Setter
@NoArgsConstructor
public class HrRuleVersion extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "policy_pack_id", nullable = false)
    private HrPolicyPack policyPack;

    @Column(name = "rule_type", nullable = false, length = 150)
    private String ruleType;

    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Column(name = "payload_hash", nullable = false, length = 128)
    private String payloadHash;

    @Override
    protected String getModuleCode() {
        return "HRV";
    }
}

