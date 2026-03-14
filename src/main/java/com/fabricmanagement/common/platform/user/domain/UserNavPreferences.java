package com.fabricmanagement.common.platform.user.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;
import lombok.*;
import org.hibernate.annotations.Type;

/**
 * Per-user navigation preferences: sort order and hidden nav item IDs.
 *
 * <p>One row per (tenant, user). Stored in {@code common_user.user_nav_preferences}. Used by GET
 * /api/common/users/{id}/nav-preferences and PATCH for upsert; unique (tenant_id, user_id) is
 * critical for that logic.
 *
 * <h2>Multi-Tenancy:</h2>
 *
 * <p>Inherits tenant_id from BaseEntity. All queries MUST be tenant-scoped.
 */
@Entity
@Table(
    name = "user_nav_preferences",
    schema = "common_user",
    indexes = {
      @Index(name = "idx_user_nav_preferences_tenant", columnList = "tenant_id"),
      @Index(name = "idx_user_nav_preferences_tenant_user", columnList = "tenant_id,user_id")
    },
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_user_nav_preferences_tenant_user",
          columnNames = {"tenant_id", "user_id"})
    })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserNavPreferences extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Type(JsonBinaryType.class)
  @Column(name = "sort_order", nullable = false, columnDefinition = "jsonb")
  @Builder.Default
  private List<String> sortOrder = new ArrayList<>();

  @Type(JsonBinaryType.class)
  @Column(name = "hidden_item_ids", nullable = false, columnDefinition = "jsonb")
  @Builder.Default
  private List<String> hiddenItemIds = new ArrayList<>();

  @Override
  protected String getModuleCode() {
    return "NAVPREF";
  }
}
