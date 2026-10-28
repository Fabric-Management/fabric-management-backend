package com.fabricmanagement.product.fabric.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.platform.tradingpartner.app.TradingPartnerQueryService;
import com.fabricmanagement.platform.tradingpartner.domain.PartnerStatus;
import com.fabricmanagement.product.fabric.domain.exception.FabricDomainException;
import com.fabricmanagement.product.fabric.domain.pricing.FabricFinishingPrice;
import com.fabricmanagement.product.fabric.domain.reference.FabricFinishingProcess;
import com.fabricmanagement.product.fabric.domain.vocabulary.FinishingChargeUnit;
import com.fabricmanagement.product.fabric.dto.CreateFabricFinishingPriceRequest;
import com.fabricmanagement.product.fabric.dto.FabricFinishingPriceDto;
import com.fabricmanagement.product.fabric.infra.repository.FabricFinishingPriceRepository;
import com.fabricmanagement.product.fabric.infra.repository.FabricFinishingProcessRepository;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The tenant's finishing price list (FABRIC-3c): what each process costs, in general or at one
 * firm, from a date. Prices are not edited; a new price is a new row from a later date.
 */
@Service
@RequiredArgsConstructor
public class FabricFinishingPriceService {

  private final FabricFinishingPriceRepository prices;
  private final FabricFinishingProcessRepository processes;
  private final TradingPartnerQueryService partners;

  /**
   * The tenant's live prices, optionally of one process and of one firm, in the dictionary's order,
   * the general price before the firms' tariffs and the latest date first.
   */
  @Transactional(readOnly = true)
  public List<FabricFinishingPriceDto> list(UUID processId, UUID partnerId) {
    UUID tenantId = TenantContext.requireTenantId();
    Map<UUID, FabricFinishingProcess> byId = processesById(tenantId);
    Comparator<FabricFinishingPrice> order =
        Comparator.comparing(
                (FabricFinishingPrice price) -> byId.get(price.getProcessId()),
                FabricFinishingCatalogService.ORDER)
            .thenComparing(
                FabricFinishingPrice::getPartnerId,
                Comparator.nullsFirst(Comparator.naturalOrder()))
            .thenComparing(FabricFinishingPrice::getValidFrom, Comparator.reverseOrder());
    return prices.findByTenantIdAndDeletedAtIsNull(tenantId).stream()
        .filter(price -> processId == null || processId.equals(price.getProcessId()))
        .filter(price -> partnerId == null || partnerId.equals(price.getPartnerId()))
        .filter(price -> byId.containsKey(price.getProcessId()))
        .sorted(order)
        .map(price -> FabricFinishingPriceDto.from(price, byId.get(price.getProcessId())))
        .toList();
  }

  /**
   * The price a process has at a firm on a date: the firm's own latest tariff that has started, or
   * else the latest general price that has started. Empty when nothing is priced, which is normal
   * for a firm the tenant has not agreed a tariff with. {@code partnerId} is null for work at an
   * own site.
   */
  @Transactional(readOnly = true)
  public Optional<FabricFinishingPriceDto> inForce(UUID processId, UUID partnerId, LocalDate date) {
    if (processId == null || date == null) {
      return Optional.empty();
    }
    UUID tenantId = TenantContext.requireTenantId();
    return processes
        .findByTenantIdAndIdAndDeletedAtIsNull(tenantId, processId)
        .flatMap(
            process ->
                resolve(
                        prices.findByTenantIdAndProcessIdAndDeletedAtIsNull(tenantId, processId),
                        process.getChargeUnit(),
                        partnerId,
                        date)
                    .map(price -> FabricFinishingPriceDto.from(price, process)));
  }

  @Transactional
  public FabricFinishingPriceDto create(CreateFabricFinishingPriceRequest request) {
    UUID tenantId = TenantContext.requireTenantId();
    FabricFinishingProcess process =
        processes
            .findByTenantIdAndIdAndDeletedAtIsNull(tenantId, request.processId())
            .orElseThrow(
                () -> new NotFoundException("Finishing process not found: " + request.processId()));
    if (!process.isUsable()) {
      throw new FabricDomainException(
          "Finishing process " + process.getCode() + " is retired; it cannot be priced",
          "FINISHING_PRICE_PROCESS_RETIRED",
          422);
    }
    UUID partnerId = activePartner(tenantId, request.partnerId());
    boolean taken =
        prices.findByTenantIdAndProcessIdAndDeletedAtIsNull(tenantId, process.getId()).stream()
            .anyMatch(
                price ->
                    Objects.equals(price.getPartnerId(), partnerId)
                        && price.getValidFrom().equals(request.validFrom()));
    if (taken) {
      throw duplicate(process, request.validFrom());
    }
    FabricFinishingPrice price =
        FabricFinishingPrice.record(
            tenantId,
            process.getId(),
            partnerId,
            process.getChargeUnit(),
            request.unitPrice(),
            request.currency(),
            request.validFrom());
    try {
      prices.saveAndFlush(price);
    } catch (DataIntegrityViolationException raced) {
      throw duplicate(process, request.validFrom());
    }
    return FabricFinishingPriceDto.from(price, process);
  }

  /** Withdraws a price that was entered by mistake; an earlier price applies again. */
  @Transactional
  public void remove(UUID id) {
    FabricFinishingPrice price =
        prices
            .findByTenantIdAndIdAndDeletedAtIsNull(TenantContext.requireTenantId(), id)
            .orElseThrow(() -> new NotFoundException("Finishing price not found: " + id));
    price.withdraw();
    prices.save(price);
  }

  /**
   * Picks the price in force. Only prices in the process's current charge unit count, so a process
   * moved from metres to kilograms never reads an old per-metre price as per-kilogram.
   */
  static Optional<FabricFinishingPrice> resolve(
      Collection<FabricFinishingPrice> candidates,
      FinishingChargeUnit unit,
      UUID partnerId,
      LocalDate date) {
    Comparator<FabricFinishingPrice> latest =
        Comparator.comparing(FabricFinishingPrice::getValidFrom);
    List<FabricFinishingPrice> started =
        candidates.stream()
            .filter(price -> price.getUnit() == unit && price.startedBy(date))
            .toList();
    Optional<FabricFinishingPrice> firm =
        partnerId == null
            ? Optional.empty()
            : started.stream().filter(price -> partnerId.equals(price.getPartnerId())).max(latest);
    return firm.isPresent()
        ? firm
        : started.stream().filter(price -> !price.isFirmTariff()).max(latest);
  }

  private Map<UUID, FabricFinishingProcess> processesById(UUID tenantId) {
    return processes.findByTenantIdAndDeletedAtIsNull(tenantId).stream()
        .collect(Collectors.toMap(FabricFinishingProcess::getId, Function.identity()));
  }

  private UUID activePartner(UUID tenantId, UUID partnerId) {
    if (partnerId == null) {
      return null;
    }
    boolean usable =
        partners
            .findClassification(tenantId, partnerId)
            .filter(partner -> partner.active() && partner.status() == PartnerStatus.ACTIVE)
            .isPresent();
    if (!usable) {
      throw new NotFoundException("Active trading partner not found: " + partnerId);
    }
    return partnerId;
  }

  private static FabricDomainException duplicate(FabricFinishingProcess process, LocalDate from) {
    return new FabricDomainException(
        "Finishing process "
            + process.getCode()
            + " already has a price from "
            + from
            + " for this firm; withdraw it first or choose another date",
        "FINISHING_PRICE_DUPLICATE",
        409);
  }
}
