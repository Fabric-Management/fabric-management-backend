package com.fabricmanagement.common.platform.company.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/**
 * Department within a company.
 *
 * <p>Represents organizational units within a company such as:
 * <ul>
 *   <li>Production</li>
 *   <li>Planning</li>
 *   <li>Finance</li>
 *   <li>Quality Control</li>
 *   <li>Logistics</li>
 * </ul>
 *
 * <h2>Usage in Authorization:</h2>
 * <p>Department is used in Layer 3 of the Policy Engine to control
 * access based on organizational structure.</p>
 *
 * <h2>Example:</h2>
 * <pre>{@code
 * Department production = Department.builder()
 *     .companyId(company.getId())
 *     .departmentName("production")
 *     .description("Production Department")
 *     .build();
 * }</pre>
 */
@Entity
@Table(name = "common_department", schema = "common_company")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Department extends BaseEntity {

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "department_name", nullable = false, length = 100)
    private String departmentName;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "manager_id")
    private UUID managerId;

    public static Department create(UUID companyId, String departmentName, String description) {
        return Department.builder()
            .companyId(companyId)
            .departmentName(departmentName)
            .description(description)
            .build();
    }

    public void assignManager(UUID managerId) {
        this.managerId = managerId;
    }

    public void removeManager() {
        this.managerId = null;
    }

    public boolean hasManager() {
        return this.managerId != null;
    }

    @Override
    protected String getModuleCode() {
        return "DEPT";
    }
}

