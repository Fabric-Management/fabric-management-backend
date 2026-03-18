package com.fabricmanagement.flowboard.task.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.*;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Task'lara atanabilen renkli etiket.
 *
 * <p>board_id NULL ise global etiket (tüm board'larda geçerli). board_id set ise sadece o board'a
 * özel etiket.
 *
 * <p>Docs: {@code 07-flowboard/board-task.md} — Bölüm 6 TaskLabel
 */
@Entity
@Table(
    schema = "flowboard",
    name = "task_label",
    uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "name", "board_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TaskLabel extends BaseEntity {

  @Column(nullable = false, length = 100)
  private String name;

  /** HEX renk kodu — örn. #E74C3C */
  @Column(nullable = false, length = 7)
  private String color;

  /** Emoji ikonu — örn. 🔴, ⭐ */
  @Column(length = 10)
  private String icon;

  /** NULL = global etiket (tüm board'larda geçerli). */
  @Column(name = "board_id")
  private UUID boardId;

  @Override
  protected String getModuleCode() {
    return "LBL";
  }

  /** Global etiket oluşturur (board_id = null). */
  public static TaskLabel createGlobal(String name, String color, String icon) {
    return create(name, color, icon, null);
  }

  /** Board'a özel etiket oluşturur. */
  public static TaskLabel createForBoard(UUID boardId, String name, String color, String icon) {
    return create(name, color, icon, boardId);
  }

  private static TaskLabel create(String name, String color, String icon, UUID boardId) {
    var label = new TaskLabel();
    label.name = name;
    label.color = color != null ? color : "#3498DB";
    label.icon = icon;
    label.boardId = boardId;
    return label;
  }

  /** Etiket bilgilerini günceller. */
  public void update(String name, String color, String icon) {
    this.name = name;
    if (color != null) this.color = color;
    this.icon = icon;
  }

  /** Global etiket mi? */
  public boolean isGlobal() {
    return this.boardId == null;
  }
}
