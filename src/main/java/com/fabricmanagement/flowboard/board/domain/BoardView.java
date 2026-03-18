package com.fabricmanagement.flowboard.board.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.*;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Board üzerindeki kayıtlı görünüm konfigürasyonu.
 *
 * <p>Her board birden fazla görünüme sahip olabilir. Kullanıcılar kişisel veya paylaşılan
 * görünümler oluşturabilir.
 *
 * <p>Docs: {@code 07-flowboard/board-task.md} — Bölüm 3 BoardView
 */
@Entity
@Table(schema = "flowboard", name = "board_view")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BoardView extends BaseEntity {

  @Column(name = "board_id", nullable = false)
  private UUID boardId;

  @Column(nullable = false, length = 255)
  private String name;

  @Enumerated(EnumType.STRING)
  @Column(name = "view_type", nullable = false, length = 20)
  private ViewType viewType;

  /**
   * Görünüm konfigürasyonu — JSONB.
   *
   * <pre>
   * {
   *   "filters": [...],
   *   "sortBy": [...],
   *   "hiddenColumns": [...],
   *   "groupBy": "status",
   *   "kanbanConfig": { "columnField": "status", "swimlaneField": "moduleType" }
   * }
   * </pre>
   */
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "jsonb")
  private String config;

  @Column(name = "is_default", nullable = false)
  private Boolean isDefault = false;

  @Column(name = "created_by_user_id")
  private UUID createdByUserId;

  @Column(name = "is_shared", nullable = false)
  private Boolean isShared = true;

  @Override
  protected String getModuleCode() {
    return "BVEW";
  }

  public static BoardView create(
      UUID boardId,
      String name,
      ViewType viewType,
      Boolean isDefault,
      UUID createdByUserId,
      Boolean isShared) {
    var view = new BoardView();
    view.boardId = boardId;
    view.name = name;
    view.viewType = viewType;
    view.isDefault = isDefault != null ? isDefault : false;
    view.createdByUserId = createdByUserId;
    view.isShared = isShared != null ? isShared : true;
    return view;
  }

  /** Görünümü varsayılan olarak işaretler. */
  public void markAsDefault() {
    this.isDefault = true;
  }

  /** Varsayılan işaretini kaldırır. */
  public void unmarkAsDefault() {
    this.isDefault = false;
  }

  /** Config JSONB günceller. */
  public void updateConfig(String config) {
    this.config = config;
  }
}
