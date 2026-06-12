package com.fabricmanagement.flowboard.board.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import com.fabricmanagement.flowboard.common.exception.FlowBoardDomainException;
import jakarta.persistence.*;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * FlowBoard genelinde tüm operasyonel task'ların organize edildiği board.
 *
 * <p>Her board belirli bir modüle (BoardType) özgüdür. GLOBAL board sadece Manager/Admin için tüm
 * modülleri kapsar.
 *
 * <p>Docs: {@code 07-flowboard/board-task.md} — Bölüm 1 Board
 */
@Entity
@Table(
    schema = "flowboard",
    name = "board",
    uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "board_type"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Board extends BaseEntity {

  @Column(nullable = false, length = 255)
  private String name;

  @Enumerated(EnumType.STRING)
  @Column(name = "board_type", nullable = false, length = 30)
  private BoardType boardType;

  @Column(name = "wip_limit_default", nullable = false)
  private Integer wipLimitDefault = 5;

  @Enumerated(EnumType.STRING)
  @Column(name = "default_view_type", nullable = false, length = 20)
  private ViewType defaultViewType = ViewType.KANBAN;

  @Column(columnDefinition = "TEXT")
  private String description;

  /** Panodan ve dolayısıyla eskale edilecek görevlerden sorumlu yönetici. */
  @Column(name = "manager_user_id")
  private UUID managerUserId;

  @Override
  protected String getModuleCode() {
    return "BRD";
  }

  /**
   * Create new boardur.
   *
   * @param name Board adı
   * @param boardType Board tipi
   * @param wipLimitDefault Varsayılan WIP limiti
   * @param defaultViewType Varsayılan görünüm tipi
   * @param description Opsiyonel açıklama
   */
  public static Board create(
      String name,
      BoardType boardType,
      Integer wipLimitDefault,
      ViewType defaultViewType,
      String description) {
    var board = new Board();
    board.name = name;
    board.boardType = boardType;
    board.wipLimitDefault = wipLimitDefault != null ? wipLimitDefault : 5;
    board.defaultViewType = defaultViewType != null ? defaultViewType : ViewType.KANBAN;
    board.description = description;
    return board;
  }

  /** Board adını günceller. */
  public void updateName(String name) {
    this.name = name;
  }

  /** WIP limitini günceller. */
  public void updateWipLimit(Integer wipLimit) {
    if (wipLimit == null || wipLimit < 1) {
      throw new FlowBoardDomainException("WIP limit must be a positive integer, got: " + wipLimit);
    }
    this.wipLimitDefault = wipLimit;
  }

  /** Varsayılan görünümü günceller. */
  public void updateDefaultView(ViewType viewType) {
    this.defaultViewType = viewType;
  }

  /** Pano yöneticisini günceller. */
  public void updateManagerUserId(UUID managerUserId) {
    this.managerUserId = managerUserId;
  }
}
