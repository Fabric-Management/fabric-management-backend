package com.fabricmanagement.platform.organization.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.fabricmanagement.platform.subscription.app.JobTitleSeedData;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class SystemDepartmentTest {

  @Test
  void permissionBackedCodesMatchTemplateDepartmentCodes() {
    assertThat(SystemDepartment.permissionBackedCodes())
        .containsExactlyInAnyOrder(
            "SALES",
            "FIBER",
            "YARN",
            "WEAVING",
            "KNITTING",
            "DYEING",
            "GARMENT",
            "QUALITY",
            "WAREHOUSE",
            "FINANCE",
            "HR",
            "PROCUREMENT");
  }

  @Test
  void jobTitleDepartmentCodesAreCanonicalSystemDepartments() {
    Set<String> canonicalCodes =
        Set.of(SystemDepartment.values()).stream()
            .map(SystemDepartment::code)
            .collect(Collectors.toSet());

    assertThat(JobTitleSeedData.ALL_PRESETS)
        .extracting(JobTitleSeedData.Preset::departmentCode)
        .allSatisfy(code -> assertThat(canonicalCodes).contains(code));
  }
}
