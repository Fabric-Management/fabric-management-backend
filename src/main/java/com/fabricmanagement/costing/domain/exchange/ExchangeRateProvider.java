package com.fabricmanagement.costing.domain.exchange;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

public interface ExchangeRateProvider {
  /**
   * Verilen tarih için kuru döner. Empty = kur bulunamadı → caller karar verir (kullanıcıya sor
   * veya hata fırlat).
   */
  Optional<BigDecimal> getRate(String fromCurrency, String toCurrency, LocalDate date);
}
