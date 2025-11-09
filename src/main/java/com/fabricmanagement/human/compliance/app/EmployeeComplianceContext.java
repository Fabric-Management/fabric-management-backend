package com.fabricmanagement.human.compliance.app;

import java.util.Optional;

public record EmployeeComplianceContext(String department) {

    public static EmployeeComplianceContext of(String department) {
        return new EmployeeComplianceContext(department);
    }

    public Optional<String> departmentValue() {
        return Optional.ofNullable(department);
    }
}

