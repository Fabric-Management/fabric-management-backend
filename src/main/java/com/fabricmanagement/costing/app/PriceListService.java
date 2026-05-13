package com.fabricmanagement.costing.app;

import com.fabricmanagement.costing.domain.currency.CostHistory;
import com.fabricmanagement.costing.domain.currency.ExchangeRateSnapshot;
import com.fabricmanagement.costing.domain.currency.ExchangeRateSource;
import com.fabricmanagement.costing.domain.exception.CostingDomainException;
import com.fabricmanagement.costing.domain.price.PriceList;
import com.fabricmanagement.costing.infra.repository.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service for managing price lists and exchange rate snapshots.
 *
 * <p>Responsibilities:
 *
 * <ul>
 *   <li>CRUD for PriceList / PriceListItem / VolumePriceBreak
 *   <li>Exchange rate snapshot capture and lookup
 *   <li>Cost history recording when a price changes
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PriceListService {

  private final PriceListRepository priceListRepo;
  private final PriceListItemRepository priceListItemRepo;
  private final ExchangeRateSnapshotRepository exchangeRateRepo;
  private final CostHistoryRepository costHistoryRepo;

  // ============================================================
  // PRICE LIST CRUD
  // ============================================================

  /** Create a new price list for a module + tenant. */
  @Transactional
  public PriceList createPriceList(
      UUID tenantId,
      String name,
      String moduleType,
      String currency,
      LocalDate validFrom,
      LocalDate validUntil,
      String seasonTag) {
    if (validUntil != null && validFrom.isAfter(validUntil)) {
      throw new CostingDomainException("validFrom cannot be after validUntil");
    }
    var pl =
        PriceList.create(tenantId, name, moduleType, currency, validFrom, validUntil, seasonTag);
    return priceListRepo.save(pl);
  }

  /**
   * Find active price list for module on today's date.
   *
   * <p><strong>Internal use only.</strong> External callers (e.g. other modules) should trigger
   * cost calculations via {@link CostCalculationService}, not access price lists directly.
   */
  @Transactional(readOnly = true)
  public PriceList findActive(UUID tenantId, String moduleType) {
    return priceListRepo
        .findActiveForModule(tenantId, moduleType, LocalDate.now())
        .orElseThrow(
            () -> new CostingDomainException("No active price list for module: " + moduleType));
  }

  @Transactional(readOnly = true)
  public List<PriceList> listPriceLists(UUID tenantId, String moduleType) {
    return priceListRepo.findByTenantIdAndModuleTypeAndIsActiveTrueOrderByValidFromDesc(
        tenantId, moduleType);
  }

  @Transactional
  public void deactivatePriceList(UUID priceListId) {
    PriceList pl =
        priceListRepo
            .findById(priceListId)
            .orElseThrow(() -> new CostingDomainException("Price list not found: " + priceListId));
    pl.delete();
    priceListRepo.save(pl);
  }

  // ============================================================
  // SUPPLIER PRICE UPDATE (called on GoodsReceiptConfirmed)
  // ============================================================

  /**
   * Update a supplier-specific price in the active price list with the actual purchase price from a
   * confirmed Goods Receipt. Appends a CostHistory record for trend analysis.
   *
   * @param tenantId owning tenant
   * @param moduleType the module type
   * @param costItemCode the cost item being updated
   * @param productId the specific product
   * @param tradingPartnerId the supplier who delivered
   * @param actualUnitPrice the real purchase price per unit
   * @param currency currency of the price
   */
  @Transactional
  public void updateActualPurchasePrice(
      UUID tenantId,
      String moduleType,
      String costItemCode,
      UUID productId,
      UUID tradingPartnerId,
      BigDecimal actualUnitPrice,
      String currency) {

    PriceList activeList =
        priceListRepo.findActiveForModule(tenantId, moduleType, LocalDate.now()).orElse(null);
    if (activeList == null) {
      log.warn("No active price list for module={} — skipping actual price update", moduleType);
      return;
    }

    // Update or no-op the PriceListItem
    priceListItemRepo
        .findBest(activeList.getId(), costItemCode, productId, tradingPartnerId)
        .filter(
            item ->
                item.getTradingPartnerId() != null
                    && item.getTradingPartnerId().equals(tradingPartnerId))
        .ifPresent(
            item -> {
              item.setUnitPrice(actualUnitPrice);
              item.setCurrency(currency);
              priceListItemRepo.save(item);
              log.info(
                  "PriceListItem updated with actual price: costItemCode={} partnerId={} price={}",
                  costItemCode,
                  tradingPartnerId,
                  actualUnitPrice);
            });

    // Append CostHistory record (use setters — tenantId is inherited from BaseEntity, not in
    // builder)
    CostHistory history = new CostHistory();
    history.setTenantId(tenantId);
    history.setCostItemCode(costItemCode);
    history.setModuleType(moduleType);
    history.setProductId(productId);
    history.setUnitPrice(actualUnitPrice);
    history.setCurrency(currency);
    history.setValidFrom(LocalDate.now());
    history.setChangeReason("GoodsReceipt actual price capture");
    costHistoryRepo.save(history);
  }

  // ============================================================
  // EXCHANGE RATE
  // ============================================================

  /** Manually record an exchange rate snapshot (finance user override or batch import). */
  @Transactional
  public ExchangeRateSnapshot captureExchangeRate(
      UUID tenantId,
      String baseCurrency,
      String targetCurrency,
      BigDecimal rate,
      ExchangeRateSource source) {
    if (rate == null || rate.compareTo(BigDecimal.ZERO) <= 0) {
      throw new CostingDomainException("Exchange rate must be positive");
    }
    var snap = ExchangeRateSnapshot.capture(tenantId, baseCurrency, targetCurrency, rate, source);
    return exchangeRateRepo.save(snap);
  }

  @Transactional(readOnly = true)
  public ExchangeRateSnapshot findLatestRate(String baseCurrency, String targetCurrency) {
    return exchangeRateRepo
        .findLatest(baseCurrency, targetCurrency)
        .orElseThrow(
            () ->
                new CostingDomainException(
                    "No exchange rate snapshot found for: "
                        + baseCurrency
                        + " → "
                        + targetCurrency));
  }
}
