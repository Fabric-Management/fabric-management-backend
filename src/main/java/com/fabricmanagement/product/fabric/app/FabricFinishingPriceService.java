package com.fabricmanagement.product.fabric.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.platform.tradingpartner.app.TradingPartnerQueryService;
import com.fabricmanagement.platform.tradingpartner.domain.PartnerStatus;
import com.fabricmanagement.product.core.domain.ProductType;
import com.fabricmanagement.product.fabric.domain.exception.FabricDomainException;
import com.fabricmanagement.product.fabric.domain.pricing.FabricFinishingPrice;
import com.fabricmanagement.product.fabric.domain.pricing.FabricFinishingPriceClass;
import com.fabricmanagement.product.fabric.domain.pricing.FinishingPriceResolver;
import com.fabricmanagement.product.fabric.domain.pricing.PriceScope;
import com.fabricmanagement.product.fabric.domain.reference.FabricFinishingProcess;
import com.fabricmanagement.product.fabric.dto.CreateFabricFinishingPriceRequest;
import com.fabricmanagement.product.fabric.dto.FabricFinishingPriceDto;
import com.fabricmanagement.product.fabric.dto.FinishingPriceQuery;
import com.fabricmanagement.product.fabric.infra.repository.FabricFinishingPriceClassRepository;
import com.fabricmanagement.product.fabric.infra.repository.FabricFinishingPriceRepository;
import com.fabricmanagement.product.fabric.infra.repository.FabricFinishingProcessRepository;
import com.fabricmanagement.product.qualitygrade.api.query.QualityGradeQueryService;
import com.fabricmanagement.product.qualitygrade.api.query.QualityGradeQueryService.QualityGradeReference;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The tenant's finishing price list (FABRIC-3c): what each process costs, in general or at one
 * firm, from a date, narrowed where the firm prices that way by quality grade, dyehouse price
 * class, formation and weight range. Prices are not edited; a new price is a new row from a later
 * date.
 */
@Service
@RequiredArgsConstructor
public class FabricFinishingPriceService {

  private final FabricFinishingPriceRepository prices;
  private final FabricFinishingProcessRepository processes;
  private final FabricFinishingPriceClassRepository priceClasses;
  private final TradingPartnerQueryService partners;
  private final QualityGradeQueryService qualityGrades;

  /**
   * The tenant's live prices, optionally of one process and of one firm, in the dictionary's order,
   * the general price before the firms' tariffs, grades in rank order and the latest date first.
   */
  @Transactional(readOnly = true)
  public List<FabricFinishingPriceDto> list(UUID processId, UUID partnerId) {
    UUID tenantId = TenantContext.requireTenantId();
    Map<UUID, FabricFinishingProcess> byId = processesById(tenantId);
    List<FabricFinishingPrice> rows =
        prices.findByTenantIdAndDeletedAtIsNull(tenantId).stream()
            .filter(price -> processId == null || processId.equals(price.getProcessId()))
            .filter(price -> partnerId == null || partnerId.equals(price.getPartnerId()))
            .filter(price -> byId.containsKey(price.getProcessId()))
            .toList();
    Lookups lookups = lookups(tenantId, rows);
    Comparator<FabricFinishingPrice> order =
        Comparator.comparing(
                (FabricFinishingPrice price) -> byId.get(price.getProcessId()),
                FabricFinishingCatalogService.ORDER)
            .thenComparing(
                FabricFinishingPrice::getPartnerId,
                Comparator.nullsFirst(Comparator.<UUID>naturalOrder()))
            .thenComparing(lookups.gradeOrder())
            .thenComparing(
                (FabricFinishingPrice price) -> lookups.priceClass(price),
                Comparator.nullsFirst(FabricFinishingPriceClassService.ORDER))
            .thenComparing(FabricFinishingPrice::getValidFrom, Comparator.reverseOrder());
    return rows.stream()
        .sorted(order)
        .map(price -> dto(price, byId.get(price.getProcessId()), lookups))
        .toList();
  }

  /**
   * The prices in force for a piece of work, one per quality grade the firm prices separately (see
   * {@link FinishingPriceResolver}); the price without a grade, which covers every grade without a
   * price of its own, comes first and the rest follow in rank order. Empty when nothing is priced,
   * which is normal for a firm the tenant has not agreed a tariff with.
   */
  @Transactional(readOnly = true)
  public List<FabricFinishingPriceDto> inForce(FinishingPriceQuery query) {
    if (query == null || query.processId() == null || query.date() == null) {
      return List.of();
    }
    UUID tenantId = TenantContext.requireTenantId();
    return processes
        .findByTenantIdAndIdAndDeletedAtIsNull(tenantId, query.processId())
        .map(
            process -> {
              List<FabricFinishingPrice> winners =
                  FinishingPriceResolver.resolve(
                      prices.findByTenantIdAndProcessIdAndDeletedAtIsNull(
                          tenantId, process.getId()),
                      process.getChargeUnit(),
                      query.partnerId(),
                      query.date(),
                      query.context());
              Lookups lookups = lookups(tenantId, winners);
              return winners.stream()
                  .sorted(lookups.gradeOrder())
                  .map(price -> dto(price, process, lookups))
                  .toList();
            })
        .orElse(List.of());
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
    QualityGradeReference grade = fabricGrade(request.qualityGradeId());
    FabricFinishingPriceClass priceClass = usablePriceClass(tenantId, request.priceClassId());
    PriceScope scope =
        new PriceScope(
            partnerId,
            request.billingBasis(),
            grade == null ? null : grade.id(),
            priceClass == null ? null : priceClass.getId(),
            request.formation(),
            request.gsmFrom(),
            request.gsmTo(),
            request.gsmBasis());
    boolean taken =
        prices.findByTenantIdAndProcessIdAndDeletedAtIsNull(tenantId, process.getId()).stream()
            .anyMatch(price -> price.sameSlotAs(scope, request.validFrom()));
    if (taken) {
      throw duplicate(process, request.validFrom());
    }
    FabricFinishingPrice price =
        FabricFinishingPrice.record(
            tenantId,
            process.getId(),
            scope,
            process.getChargeUnit(),
            request.unitPrice(),
            request.currency(),
            request.validFrom());
    try {
      prices.saveAndFlush(price);
    } catch (DataIntegrityViolationException raced) {
      throw duplicate(process, request.validFrom());
    }
    return FabricFinishingPriceDto.from(price, process, priceClass, grade);
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

  private Map<UUID, FabricFinishingProcess> processesById(UUID tenantId) {
    return processes.findByTenantIdAndDeletedAtIsNull(tenantId).stream()
        .collect(Collectors.toMap(FabricFinishingProcess::getId, Function.identity()));
  }

  private Lookups lookups(UUID tenantId, Collection<FabricFinishingPrice> rows) {
    Set<UUID> gradeIds =
        rows.stream()
            .map(FabricFinishingPrice::getQualityGradeId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
    return new Lookups(
        priceClasses.findByTenantIdAndDeletedAtIsNull(tenantId).stream()
            .collect(Collectors.toMap(FabricFinishingPriceClass::getId, Function.identity())),
        qualityGrades.findReferencesByIds(gradeIds).stream()
            .collect(Collectors.toMap(QualityGradeReference::id, Function.identity())));
  }

  private FabricFinishingPriceDto dto(
      FabricFinishingPrice price, FabricFinishingProcess process, Lookups lookups) {
    return FabricFinishingPriceDto.from(
        price, process, lookups.priceClass(price), lookups.grade(price));
  }

  /** The grade must be an active fabric quality grade of the tenant; none means every grade. */
  private QualityGradeReference fabricGrade(UUID gradeId) {
    if (gradeId == null) {
      return null;
    }
    return qualityGrades.findSalesGradeReferences(ProductType.FABRIC, true).stream()
        .filter(grade -> grade.id().equals(gradeId))
        .findFirst()
        .orElseThrow(
            () -> new NotFoundException("Active fabric quality grade not found: " + gradeId));
  }

  private FabricFinishingPriceClass usablePriceClass(UUID tenantId, UUID priceClassId) {
    if (priceClassId == null) {
      return null;
    }
    FabricFinishingPriceClass priceClass =
        priceClasses
            .findByTenantIdAndIdAndDeletedAtIsNull(tenantId, priceClassId)
            .orElseThrow(
                () -> new NotFoundException("Finishing price class not found: " + priceClassId));
    if (!priceClass.isUsable()) {
      throw new FabricDomainException(
          "Finishing price class " + priceClass.getCode() + " is retired; it cannot be priced",
          "FINISHING_PRICE_CLASS_RETIRED",
          422);
    }
    return priceClass;
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
            + " for this firm and this narrowing; withdraw it first or choose another date",
        "FINISHING_PRICE_DUPLICATE",
        409);
  }

  /** The price classes and grades the rows being shown point at. */
  private record Lookups(
      Map<UUID, FabricFinishingPriceClass> classes, Map<UUID, QualityGradeReference> grades) {

    FabricFinishingPriceClass priceClass(FabricFinishingPrice price) {
      return price.getPriceClassId() == null ? null : classes.get(price.getPriceClassId());
    }

    QualityGradeReference grade(FabricFinishingPrice price) {
      return price.getQualityGradeId() == null ? null : grades.get(price.getQualityGradeId());
    }

    /** The price without a grade first, then the grades by rank. */
    Comparator<FabricFinishingPrice> gradeOrder() {
      return Comparator.comparing(
          (FabricFinishingPrice price) -> {
            QualityGradeReference grade = grade(price);
            return price.getQualityGradeId() == null
                ? Integer.MIN_VALUE
                : grade == null ? Integer.MAX_VALUE : grade.rank();
          });
    }
  }
}
