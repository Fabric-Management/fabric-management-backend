package com.fabricmanagement.approval.domain;

public enum PolicyTargetLevel {
  ALL, // Herkes için onay zorunlu
  PROBATION, // Sadece Probation seviyesindekiler için
  STANDARD // Standard ve altı için
}
