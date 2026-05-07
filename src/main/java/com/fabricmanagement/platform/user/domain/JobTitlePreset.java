package com.fabricmanagement.platform.user.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.*;
import java.util.Locale;
import lombok.*;

@Entity
@Table(
    name = "job_title_preset",
    schema = "common_user",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uq_job_title_tenant_code",
            columnNames = {"tenant_id", "job_title_code"}))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobTitlePreset extends BaseEntity {

  @Column(name = "job_title_code", nullable = false, length = 50)
  private String jobTitleCode;

  @Column(name = "name", nullable = false, length = 100)
  private String name;

  @Column(name = "description", length = 500)
  private String description;

  @Column(name = "department_code", length = 50)
  private String departmentCode;

  @Column(name = "role_code", length = 50)
  private String roleCode;

  @Column(name = "is_system", nullable = false)
  @Builder.Default
  private boolean isSystem = false;

  // ─── Factory Methods ───

  /** Creates a system preset from seed data. Code is fixed and provided explicitly. */
  public static JobTitlePreset createSystem(
      String code, String name, String description, String roleCode, String departmentCode) {
    return JobTitlePreset.builder()
        .jobTitleCode(code)
        .name(name)
        .description(description)
        .roleCode(roleCode)
        .departmentCode(departmentCode)
        .isSystem(true)
        .build();
  }

  /** Creates a custom preset for a tenant admin. Code is auto-generated from the name. */
  public static JobTitlePreset createCustom(
      String name, String description, String roleCode, String departmentCode) {
    return JobTitlePreset.builder()
        .jobTitleCode(generateCode(name))
        .name(name)
        .description(description)
        .roleCode(roleCode)
        .departmentCode(departmentCode)
        .isSystem(false)
        .build();
  }

  // ─── Domain Methods ───

  /** Updates display name. For custom presets, the code is also regenerated. */
  public void updateName(String newName) {
    this.name = newName;
    if (!this.isSystem) {
      this.jobTitleCode = generateCode(newName);
    }
  }

  /** Full update — only allowed for custom presets. */
  public void updateFull(
      String newName, String newDescription, String newDepartmentCode, String newRoleCode) {
    if (this.isSystem) {
      throw new IllegalStateException(
          "System job titles cannot be fully updated. Use updateName() instead.");
    }
    this.name = newName;
    this.jobTitleCode = generateCode(newName);
    this.description = newDescription;
    this.departmentCode = newDepartmentCode;
    this.roleCode = newRoleCode;
  }

  /** Validates that this preset can be hard-deleted. System presets cannot be hard-deleted. */
  public void validateHardDeletion() {
    if (this.isSystem) {
      throw new IllegalStateException(
          "System job titles cannot be hard-deleted. Use soft-delete (BaseEntity.delete()) instead.");
    }
  }

  // ─── Code Generation ───

  /**
   * Generates a stable, locale-independent code from the given name.
   *
   * <p>Uses {@link Locale#ENGLISH} to avoid Turkish İ/i locale bugs. Aligned with {@code
   * TenantSeedService.generateDepartmentCode()} pattern.
   */
  public static String generateCode(String name) {
    String code = name.toUpperCase(Locale.ENGLISH).replaceAll("[^A-Z0-9]", "");
    return code.substring(0, Math.min(50, code.length()));
  }

  @Override
  protected String getModuleCode() {
    return "JTPR";
  }
}
