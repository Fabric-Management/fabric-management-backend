package com.fabricmanagement.common.platform.company.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Department Category - Groups departments into logical categories.
 *
 * <p>Represents high-level categorization of departments such as:
 * <ul>
 *   <li>Production - Production-related departments</li>
 *   <li>Administrative - Office / management / support units</li>
 *   <li>Utility - Auxiliary service units</li>
 *   <li>Logistics & Warehouse - Warehouse / shipping / inventory operations</li>
 *   <li>Support & Audit - Training / documentation / audit units</li>
 * </ul>
 *
 * <h2>Relationship:</h2>
 * <p>One DepartmentCategory can have Many Departments (One-to-Many)</p>
 *
 * <h2>Example:</h2>
 * <pre>{@code
 * DepartmentCategory production = DepartmentCategory.builder()
 *     .categoryName("Production")
 *     .description("Production-related departments")
 *     .displayOrder(1)
 *     .build();
 * }</pre>
 */
@Entity
@Table(name = "common_department_category", schema = "common_company",
    indexes = {
        @Index(name = "idx_department_category_tenant", columnList = "tenant_id"),
        @Index(name = "idx_department_category_active", columnList = "is_active"),
        @Index(name = "idx_department_category_display_order", columnList = "display_order")
    })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DepartmentCategory extends BaseEntity {

    @Column(name = "category_name", nullable = false, length = 100)
    private String categoryName;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "display_order")
    private Integer displayOrder;

    @OneToMany(mappedBy = "departmentCategory", cascade = CascadeType.ALL, orphanRemoval = false)
    @Builder.Default
    private List<Department> departments = new ArrayList<>();

    public static DepartmentCategory create(String categoryName, String description, Integer displayOrder) {
        return DepartmentCategory.builder()
            .categoryName(categoryName)
            .description(description)
            .displayOrder(displayOrder)
            .build();
    }

    public void addDepartment(Department department) {
        if (!departments.contains(department)) {
            departments.add(department);
            department.setDepartmentCategory(this);
        }
    }

    public void removeDepartment(Department department) {
        departments.remove(department);
        department.setDepartmentCategory(null);
    }

    @Override
    protected String getModuleCode() {
        return "CAT";
    }
}

