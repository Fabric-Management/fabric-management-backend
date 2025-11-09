package com.fabricmanagement.human.compliance.localization.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "human_hr_policy_pack", schema = "human",
    indexes = {
        @Index(name = "idx_hr_policy_pack_tenant_country", columnList = "tenant_id,country_code,status"),
        @Index(name = "idx_hr_policy_pack_code", columnList = "tenant_id,pack_code,pack_version", unique = true)
    })
@Getter
@Setter
@NoArgsConstructor
public class HrPolicyPack extends BaseEntity {

    @Column(name = "pack_code", nullable = false, length = 100)
    private String packCode;

    @Column(name = "country_code", nullable = false, length = 8)
    private String countryCode;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "pack_version", nullable = false)
    private Integer packVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private HrPolicyPackStatus status = HrPolicyPackStatus.DRAFT;

    @Column(name = "effective_from")
    private Instant effectiveFrom;

    @Column(name = "effective_to")
    private Instant effectiveTo;

    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Column(name = "checksum", length = 128)
    private String checksum;

    @Column(name = "parent_pack_id")
    private UUID parentPackId;

    @Column(name = "parent_pack_code", length = 100)
    private String parentPackCode;

    @Column(name = "region_code", length = 50)
    private String regionCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "inheritance_mode", nullable = false, length = 20)
    private HrInheritanceMode inheritanceMode = HrInheritanceMode.FULL;

    @OneToMany(mappedBy = "policyPack", cascade = CascadeType.ALL, orphanRemoval = true)
    private final List<HrRuleVersion> ruleVersions = new ArrayList<>();

    @OneToMany(mappedBy = "policyPack", cascade = CascadeType.ALL, orphanRemoval = true)
    private final List<HrPolicyBinding> policyBindings = new ArrayList<>();

    @Builder
    public HrPolicyPack(String packCode,
                        String countryCode,
                        String name,
                        String description,
                        Integer packVersion,
                        HrPolicyPackStatus status,
                        Instant effectiveFrom,
                        Instant effectiveTo,
                        String payload,
                        String checksum,
                        UUID parentPackId,
                        String parentPackCode,
                        String regionCode,
                        HrInheritanceMode inheritanceMode) {
        this.packCode = packCode;
        this.countryCode = countryCode;
        this.name = name;
        this.description = description;
        this.packVersion = packVersion;
        this.status = status != null ? status : HrPolicyPackStatus.DRAFT;
        this.effectiveFrom = effectiveFrom;
        this.effectiveTo = effectiveTo;
        this.payload = payload;
        this.checksum = checksum;
        this.parentPackId = parentPackId;
        this.parentPackCode = parentPackCode;
        this.regionCode = regionCode;
        if (inheritanceMode != null) {
            this.inheritanceMode = inheritanceMode;
        }
    }

    public boolean isActiveAt(Instant moment) {
        if (status != HrPolicyPackStatus.ACTIVE) {
            return false;
        }
        boolean afterStart = effectiveFrom == null || !moment.isBefore(effectiveFrom);
        boolean beforeEnd = effectiveTo == null || moment.isBefore(effectiveTo);
        return afterStart && beforeEnd;
    }

    public void replaceRuleVersions(List<HrRuleVersion> versions) {
        this.ruleVersions.clear();
        if (versions == null) {
            return;
        }
        versions.forEach(this::addRuleVersion);
    }

    public void addRuleVersion(HrRuleVersion version) {
        version.setPolicyPack(this);
        this.ruleVersions.add(version);
    }

    public void replaceBindings(List<HrPolicyBinding> bindings) {
        this.policyBindings.clear();
        if (bindings == null) {
            return;
        }
        bindings.forEach(this::addBinding);
    }

    public void addBinding(HrPolicyBinding binding) {
        binding.setPolicyPack(this);
        this.policyBindings.add(binding);
    }

    public void markPublished(Instant effectiveFrom, Instant effectiveTo) {
        this.status = HrPolicyPackStatus.ACTIVE;
        this.effectiveFrom = effectiveFrom;
        this.effectiveTo = effectiveTo;
    }

    public void markRetired(Instant effectiveTo) {
        this.status = HrPolicyPackStatus.RETIRED;
        this.effectiveTo = effectiveTo;
    }

    public void setChecksum(String checksum) {
        this.checksum = checksum;
    }

    public void updateDraft(String name, String description, String payload) {
        this.name = name;
        this.description = description;
        this.payload = payload;
    }

    public void updateHierarchy(UUID parentPackId, String parentPackCode, String regionCode, HrInheritanceMode mode) {
        this.parentPackId = parentPackId;
        this.parentPackCode = parentPackCode;
        this.regionCode = regionCode;
        if (mode != null) {
            this.inheritanceMode = mode;
        }
    }

    @Override
    protected String getModuleCode() {
        return "HRP";
    }
}
