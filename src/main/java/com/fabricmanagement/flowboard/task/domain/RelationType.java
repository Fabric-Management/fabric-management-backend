package com.fabricmanagement.flowboard.task.domain;

/** Cross-board veya intra-board görev ilişkisi türü. */
public enum RelationType {
  RELATED,
  DUPLICATES,
  CAUSED_BY,
  PARENT_CHILD
}
