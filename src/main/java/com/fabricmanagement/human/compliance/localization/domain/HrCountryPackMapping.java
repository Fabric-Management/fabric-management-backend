package com.fabricmanagement.human.compliance.localization.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.util.Locale;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
    name = "human_hr_country_pack_mapping",
    schema = "human",
    indexes = {@Index(name = "idx_country_pack_code", columnList = "tenant_id,pack_code")})
@Getter
@Setter
@NoArgsConstructor
public class HrCountryPackMapping extends BaseEntity {

  @Column(name = "country_code", nullable = false, length = 8)
  private String countryCode;

  @Column(name = "pack_code", nullable = false, length = 100)
  private String packCode;

  @Column(name = "pack_id")
  private UUID packId;

  @Builder
  public HrCountryPackMapping(String countryCode, String packCode, UUID packId) {
    this.countryCode = normalize(countryCode);
    this.packCode = packCode;
    this.packId = packId;
  }

  public void assignPack(String packCode, UUID packId) {
    this.packCode = packCode;
    this.packId = packId;
  }

  private String normalize(String country) {
    return country != null ? country.toUpperCase(Locale.ROOT) : null;
  }

  @Override
  protected String getModuleCode() {
    return "HCM";
  }
}
