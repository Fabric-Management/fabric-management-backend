package com.fabricmanagement.flowboard.task.domain;

/** TaskDependency ilişkilerindeki tip. */
public enum DependencyType {
  FINISH_TO_START, // Biri bitmeden diğeri başlayamaz (En yaygın — bloklar)
  START_TO_START, // Birlikte başlayabilir
  PARALLEL // Sadece bilgi amaçlı, bloklamaz
}
