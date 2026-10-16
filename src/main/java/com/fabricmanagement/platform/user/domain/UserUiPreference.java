package com.fabricmanagement.platform.user.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;
import lombok.*;
import org.hibernate.annotations.Type;

/**
 * One interface preference of a user, kept with the account so it follows the person to every
 * device: an ordered list of identifiers under a key the screen owns (for example the order of the
 * new sales order page's sections under {@code sales.new-order.sections}).
 *
 * <p>One row per (tenant, user, key). Stored in {@code common_user.user_ui_preference}. The values
 * are the screen's own identifiers; the backend keeps them as given and never interprets them. Rows
 * are written by one atomic upsert statement, so two saves of the same key never collide on the
 * unique constraint.
 *
 * <h2>Multi-Tenancy:</h2>
 *
 * <p>Inherits tenant_id from BaseEntity. All queries MUST be tenant-scoped.
 */
@Entity
@Table(
    name = "user_ui_preference",
    schema = "common_user",
    indexes = {
      @Index(name = "idx_user_ui_preference_tenant", columnList = "tenant_id"),
      @Index(name = "idx_user_ui_preference_tenant_user", columnList = "tenant_id,user_id")
    },
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_user_ui_preference_tenant_user_key",
          columnNames = {"tenant_id", "user_id", "preference_key"})
    })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserUiPreference extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Column(name = "preference_key", nullable = false, length = 100)
  private String preferenceKey;

  @Type(JsonBinaryType.class)
  @Column(name = "preference_values", nullable = false, columnDefinition = "jsonb")
  private List<String> preferenceValues = new ArrayList<>();

  @Override
  protected String getModuleCode() {
    return "UIPREF";
  }
}
