package com.fabricmanagement.flowboard.board.dto;

import com.fabricmanagement.flowboard.board.domain.BoardType;
import com.fabricmanagement.flowboard.board.domain.ViewType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Create new boardma isteği.
 *
 * @param name Board adı
 * @param boardType Board tipi
 * @param wipLimitDefault Varsayılan WIP limiti (null → 5)
 * @param defaultViewType Varsayılan görünüm (null → KANBAN)
 * @param description Opsiyonel açıklama
 */
public record CreateBoardRequest(
    @NotBlank @Size(max = 255) String name,
    @NotNull BoardType boardType,
    Integer wipLimitDefault,
    ViewType defaultViewType,
    String description) {}
