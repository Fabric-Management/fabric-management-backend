package com.fabricmanagement.costing.domain.template;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;
import lombok.*;
import org.hibernate.annotations.Type;

/**
 * Tenant-level configuration that groups CostItems with weights/inclusion flags for a specific
 * module type.
 *
 * <p>The {@code items} JSONB column holds a list of {@link CostTemplateItem} value objects, for
 * example:
 *
 * <pre>
 * [{"costItemCode": "RAW_PRODUCT", "weight": 0.60, "isIncluded": true},
 *  {"costItemCode": "OVERHEAD",     "weight": 0.12, "isIncluded": true}]
 * </pre>
 */
@Entity
@Table(name = "cost_template", schema = "costing")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CostTemplate extends BaseEntity {

  @Column(name = "name", nullable = false)
  private String name;

  /** Module this template applies to (e.g. "FIBER", "YARN", "FABRIC"). */
  @Column(name = "module_type", nullable = false, length = 50)
  private String moduleType;

  /** Only one default template per tenant + moduleType should be true at a time. */
  @Column(name = "is_default", nullable = false)
  @Builder.Default
  private boolean defaultTemplate = false;

  /** Ordered list of included cost items with their weights/configuration. */
  @Type(JsonType.class)
  @Column(name = "items", columnDefinition = "jsonb")
  @Builder.Default
  private List<CostTemplateItem> items = new ArrayList<>();

  /**
   * Factory method to create a named template for a given module type.
   *
   * @param tenantId the owning tenant
   * @param name human-readable template name
   * @param moduleType target production module
   * @param isDefault whether this is the tenant's default for that module
   * @param items the list of cost item configurations
   */
  public static CostTemplate create(
      java.util.UUID tenantId,
      String name,
      String moduleType,
      boolean isDefault,
      List<CostTemplateItem> items) {
    var template = new CostTemplate();
    template.setTenantId(tenantId);
    template.setName(name);
    template.setModuleType(moduleType);
    template.setDefaultTemplate(isDefault);
    template.setItems(items != null ? new ArrayList<>(items) : new ArrayList<>());
    template.onCreate();
    return template;
  }

  @Override
  protected String getModuleCode() {
    return "CTMPL";
  }
}
