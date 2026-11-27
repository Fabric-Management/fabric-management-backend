package com.fabricmanagement.product.fiber.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "prod_fiber_test_standard", schema = "production")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FiberTestStandard extends BaseEntity {

  @Column(nullable = false, length = 100, updatable = false)
  private String code;

  @Column(nullable = false, length = 255)
  private String name;

  @Column(nullable = false, length = 100, updatable = false)
  private String revision;

  public FiberTestStandard(String code, String name, String revision) {
    this.code = code.strip();
    rename(name);
    this.revision = revision == null ? "" : revision.strip();
  }

  public void rename(String name) {
    this.name = name.strip();
  }

  @Override
  protected String getModuleCode() {
    return "FTS";
  }
}
