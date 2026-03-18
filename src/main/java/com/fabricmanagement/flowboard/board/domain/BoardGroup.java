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
 * Board içindeki task'ları mantıksal gruplara ayıran yapı.
 *
 * <p>Her board'da varsayılan gruplar otomatik oluşur (Geciken / Bu Hafta / Diğer).
 *
 * <p>Docs: {@code 07-flowboard/board-task.md} — Bölüm 2 BoardGroup
 */
@Entity
@Table(schema = "flowboard", name = "board_group")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BoardGroup extends BaseEntity {

  @Column(name = "board_id", nullable = false)
  private UUID boardId;

  @Column(nullable = false, length = 255)
  private String name;

  /** HEX renk kodu — örn. #FF5733 */
  @Column(nullable = false, length = 7)
  private String color;

  @Column(name = "display_order", nullable = false)
  private Integer displayOrder = 0;

  @Column(name = "is_collapsed", nullable = false)
  private Boolean isCollapsed = false;

  @Enumerated(EnumType.STRING)
  @Column(name = "group_type", nullable = false, length = 20)
  private GroupType groupType = GroupType.MANUAL;

  /**
   * CUSTOM gruplama kuralı — JSONB.
   *
   * <pre>
   * {
   *   "rules": [{"field":"priority","operator":"IN","values":["HIGH","CRITICAL"]}],
   *   "matchType": "ALL"
   * }
   * </pre>
   */
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "filter_criteria", columnDefinition = "jsonb")
  private String filterCriteria;

  @Override
  protected String getModuleCode() {
    return "BGRP";
  }

  public static BoardGroup create(
      UUID boardId, String name, String color, Integer displayOrder, GroupType groupType) {
    var group = new BoardGroup();
    group.boardId = boardId;
    group.name = name;
    group.color = color != null ? color : "#3498DB";
    group.displayOrder = displayOrder != null ? displayOrder : 0;
    group.groupType = groupType != null ? groupType : GroupType.MANUAL;
    return group;
  }

  public static BoardGroup createCustom(
      UUID boardId, String name, String color, Integer displayOrder, String filterCriteria) {
    var group = create(boardId, name, color, displayOrder, GroupType.CUSTOM);
    group.filterCriteria = filterCriteria;
    return group;
  }

  /** Grubu daraltır/açar. */
  public void toggleCollapsed() {
    this.isCollapsed = !this.isCollapsed;
  }

  /** Sıralamayı günceller. */
  public void updateOrder(Integer order) {
    this.displayOrder = order;
  }

  /** Adı ve rengi günceller. */
  public void update(String name, String color) {
    this.name = name;
    if (color != null) {
      this.color = color;
    }
  }
}
