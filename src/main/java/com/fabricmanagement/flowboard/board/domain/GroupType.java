package com.fabricmanagement.flowboard.board.domain;

/** Board grubu tipi — task'ların nasıl gruplanacağını belirler. */
public enum GroupType {
  /** Task'lar status değerine göre otomatik gruplanır. */
  STATUS_BASED,
  /** "Bu Hafta", "Gelecek Hafta", "Geciken" gibi deadline bazlı gruplar. */
  DEADLINE_BASED,
  /** Manager tarafından manuel olarak oluşturulan gruplar. */
  MANUAL,
  /** filterCriteria JSONB ile dinamik gruplama. */
  CUSTOM
}
