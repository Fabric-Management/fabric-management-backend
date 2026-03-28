package com.fabricmanagement.flowboard.task.dto;

import com.fabricmanagement.flowboard.task.domain.TaskLabel;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

/**
 * Task'a atanmış etiket özeti — list/detay DTO'larda kullanılır.
 *
 * <p>JSON alan adı {@code colorHex}; frontend ile uyum için Java tarafında {@code color} tutulur.
 */
public record LabelResponse(UUID id, String name, @JsonProperty("colorHex") String color) {

  public static LabelResponse from(TaskLabel label) {
    return new LabelResponse(label.getId(), label.getName(), label.getColor());
  }
}
