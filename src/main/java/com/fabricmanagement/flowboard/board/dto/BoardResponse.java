package com.fabricmanagement.flowboard.board.dto;

import com.fabricmanagement.flowboard.board.domain.Board;
import com.fabricmanagement.flowboard.board.domain.BoardType;
import com.fabricmanagement.flowboard.board.domain.ViewType;
import java.util.UUID;

/**
 * Board detay yanıtı.
 *
 * @param id Board UUID
 * @param name Board adı
 * @param boardType Board tipi
 * @param wipLimitDefault WIP limiti
 * @param defaultViewType Varsayılan görünüm tipi
 * @param description Açıklama
 * @param isActive Aktif mi
 */
public record BoardResponse(
    UUID id,
    String name,
    BoardType boardType,
    Integer wipLimitDefault,
    ViewType defaultViewType,
    String description,
    Boolean isActive) {

  /** Board entity'sinden DTO oluşturur. */
  public static BoardResponse from(Board board) {
    return new BoardResponse(
        board.getId(),
        board.getName(),
        board.getBoardType(),
        board.getWipLimitDefault(),
        board.getDefaultViewType(),
        board.getDescription(),
        board.getIsActive());
  }
}
