package com.fabricmanagement.production.execution.workorder.app.adapter;

import com.fabricmanagement.platform.tradingpartner.app.TradingPartnerCertificationService;
import com.fabricmanagement.platform.tradingpartner.dto.TradingPartnerCertificationDto;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Adapter to isolate WorkOrder module from direct Trading Partner module latency/failures. Uses
 * Resilience4j Circuit Breaker and Retry for fault tolerance.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TradingPartnerAdapter {

  private final TradingPartnerCertificationService certificationService;

  @CircuitBreaker(name = "tradingPartnerService", fallbackMethod = "getCertificationsFallback")
  @Retry(name = "tradingPartnerService", fallbackMethod = "getCertificationsFallback")
  public List<TradingPartnerCertificationDto> getCertifications(UUID partnerId) {
    return certificationService.findByPartnerId(partnerId);
  }

  private List<TradingPartnerCertificationDto> getCertificationsFallback(
      UUID partnerId, Throwable t) {
    log.warn(
        "Circuit breaker open or retry failed for trading partner certifications. PartnerId: {}",
        partnerId,
        t);
    return List.of();
  }
}
