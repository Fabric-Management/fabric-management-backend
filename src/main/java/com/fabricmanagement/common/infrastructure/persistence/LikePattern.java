package com.fabricmanagement.common.infrastructure.persistence;

/** Builds escaped SQL {@code LIKE} patterns for literal user-entered text. */
public final class LikePattern {

  public static final char ESCAPE_CHARACTER = '\\';

  private LikePattern() {}

  public static String literalContains(String value) {
    String escaped = value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    return "%" + escaped + "%";
  }
}
