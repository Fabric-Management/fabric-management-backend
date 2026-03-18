package com.fabricmanagement.flowboard.task.domain;

/** Task tiplerini tanımlar — SmartTaskGenerator event → task eşlemesinde kullanılır. */
public enum TaskType {
  /** Recipe ata, WorkOrder oluştur. */
  PLANNING,
  /** Batch başlat, üretimi yönet. */
  PRODUCTION,
  /** QC kontrol yap. */
  QUALITY,
  /** Lokasyona yerleştir, stok yönet. */
  WAREHOUSE,
  /** Sevkiyat hazırla, gönder. */
  SHIPMENT,
  /** Onay bekleyen işlem. */
  APPROVAL,
  /** Manuel recipe seçimi gerekiyor. */
  RECIPE_ASSIGNMENT,
  /** Tedarikçiden temin et. */
  PROCUREMENT,
  /** Maliyet sapması inceleme. */
  COSTING,
  /** Numune hazırlama/gönderme. */
  SAMPLE,
  /** İade işleme. */
  RETURN,
  /** Stok sayımı. */
  STOCK_COUNT,
  /** Bakım görevi. */
  MAINTENANCE,
  /** Genel görev. */
  GENERAL
}
