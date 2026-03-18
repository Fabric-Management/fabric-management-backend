package com.fabricmanagement.iwm.rules.app;

import com.fabricmanagement.iwm.rules.app.event.MinStockAlertEvent;
import com.fabricmanagement.iwm.rules.app.event.ReturnRateExceededEvent;
import com.fabricmanagement.iwm.rules.domain.MinStockRule;
import com.fabricmanagement.iwm.rules.domain.ReturnRateRule;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/**
 * Stok kural motorunun tetikleme servisi. Envanter hareketi sonrasında çağrılarak MinStockRule ve
 * ReturnRateRule kontrollerini yapar ve gerektiğinde event fırlatır (CR-10-12).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StockRuleEvaluationService {

  private final ApplicationEventPublisher eventPublisher;

  /**
   * Belirli bir lokasyon+malzeme kombinasyonu için mevcut stok miktarını MinStockRule ile
   * karşılaştırır. Eşik altındaysa ve cooldown süresi dolmuşsa event fırlatır.
   */
  public void evaluateMinStockRule(MinStockRule rule, BigDecimal currentQty) {
    if (currentQty.compareTo(rule.getMinQty()) < 0) {
      // Cooldown kontrolü
      if (rule.getLastAlertAt() != null) {
        OffsetDateTime nextAllowed = rule.getLastAlertAt().plusHours(rule.getAlertCooldownHours());
        if (OffsetDateTime.now().isBefore(nextAllowed)) {
          log.debug(
              "MinStockRule for material {} on cooldown until {}",
              rule.getMaterialId(),
              nextAllowed);
          return;
        }
      }

      MinStockAlertEvent event =
          MinStockAlertEvent.builder()
              .tenantId(rule.getTenantId())
              .materialId(rule.getMaterialId())
              .locationId(rule.getLocationId())
              .currentQty(currentQty)
              .minQty(rule.getMinQty())
              .unit(rule.getUnit() != null ? rule.getUnit().name() : "")
              .build();

      eventPublisher.publishEvent(event);
      rule.updateLastAlertAt(OffsetDateTime.now());
      log.info(
          "MinStockAlert fired: material={}, location={}, current={}, min={}",
          rule.getMaterialId(),
          rule.getLocationId(),
          currentQty,
          rule.getMinQty());
    }
  }

  /** ReturnRateRule eşiğini aşan tedarikçiler için event fırlatır. */
  public void evaluateReturnRateRule(ReturnRateRule rule, BigDecimal currentReturnRate) {
    if (currentReturnRate.compareTo(rule.getThresholdRate()) > 0) {
      ReturnRateExceededEvent event =
          ReturnRateExceededEvent.builder()
              .tenantId(rule.getTenantId())
              .tradingPartnerId(rule.getTradingPartnerId())
              .currentRate(currentReturnRate)
              .thresholdRate(rule.getThresholdRate())
              .windowDays(rule.getWindowDays())
              .build();

      eventPublisher.publishEvent(event);
      log.info(
          "ReturnRateExceeded fired: partner={}, rate={}, threshold={}",
          rule.getTradingPartnerId(),
          currentReturnRate,
          rule.getThresholdRate());
    }
  }
}
