package com.fabricmanagement.production.core.batch.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.platform.tradingpartner.app.TradingPartnerQueryService;
import com.fabricmanagement.platform.tradingpartner.domain.PartnerStatus;
import com.fabricmanagement.product.color.api.query.ColorQueryService;
import com.fabricmanagement.product.core.domain.ProductType;
import com.fabricmanagement.product.fabric.api.facade.FabricFacade;
import com.fabricmanagement.product.fabric.domain.vocabulary.FabricFinishingCategory;
import com.fabricmanagement.product.fabric.dto.FabricArticleDto;
import com.fabricmanagement.product.fabric.dto.FabricFinishingPriceClassDto;
import com.fabricmanagement.product.fabric.dto.FabricFinishingPriceDto;
import com.fabricmanagement.product.fabric.dto.FabricFinishingProcessDto;
import com.fabricmanagement.product.fabric.dto.FinishingPriceQuery;
import com.fabricmanagement.production.core.batch.domain.Batch;
import com.fabricmanagement.production.core.batch.domain.BatchFinishingStep;
import com.fabricmanagement.production.core.batch.domain.BatchFinishingStepStatus;
import com.fabricmanagement.production.core.batch.domain.BatchStatus;
import com.fabricmanagement.production.core.batch.domain.FinishingGradeBreakdown;
import com.fabricmanagement.production.core.batch.domain.event.BatchFinishingReceivedEvent;
import com.fabricmanagement.production.core.batch.domain.event.BatchFinishingSentEvent;
import com.fabricmanagement.production.core.batch.domain.exception.BatchDomainException;
import com.fabricmanagement.production.core.batch.dto.BatchFinishingDtos;
import com.fabricmanagement.production.core.batch.infra.repository.BatchFinishingStepRepository;
import com.fabricmanagement.production.core.batch.infra.repository.BatchRepository;
import com.fabricmanagement.production.core.stockunit.domain.StockUnitStatus;
import com.fabricmanagement.production.core.stockunit.infra.repository.StockUnitRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What is done to a fabric lot after it is woven or knitted (FABRIC-3b): the lot goes out for a
 * process, comes back, and the step stays on the lot as its history.
 *
 * <p>The lot stays one lot. While it is out it is held, so it cannot be offered, reserved or
 * consumed; when it returns it is released again and, when the step gave it a colour, takes that
 * colour. This service never changes the lot's own quantity or its pieces: a returned length is
 * recorded on the step, and the pieces are re-measured by a separate operation.
 */
@Service
@RequiredArgsConstructor
public class BatchFinishingService {

  private static final int OPEN_LIST_LIMIT = 200;
  private static final Set<StockUnitStatus> PIECE_STATUSES_THAT_MUST_STAY =
      Set.of(StockUnitStatus.RESERVED, StockUnitStatus.IN_TRANSIT);

  private final BatchRepository batchRepository;
  private final BatchFinishingStepRepository stepRepository;
  private final StockUnitRepository stockUnitRepository;
  private final FabricFacade fabrics;
  private final ColorQueryService colours;
  private final TradingPartnerQueryService partners;
  private final FinishingStepGradeService stepGrades;
  private final ApplicationEventPublisher events;
  private final Clock clock;

  /** Sends the whole lot out for a process. */
  @Transactional
  public BatchFinishingDtos.StepDto send(UUID batchId, BatchFinishingDtos.SendRequest request) {
    UUID tenantId = TenantContext.requireTenantId();
    Batch batch = lockedFabricLot(tenantId, batchId);
    FabricFinishingProcessDto process = usableProcess(request.processId());
    UUID colourId = colourFor(process, request.colorId());
    UUID partnerId = activePartner(tenantId, request.partnerId());
    FabricFinishingPriceClassDto priceClass = usablePriceClass(request.priceClassId());
    requireLotMayLeave(tenantId, batch);

    BatchFinishingStep opened =
        BatchFinishingStep.send(
            tenantId,
            batchId,
            nextSequence(tenantId, batchId),
            process.id(),
            partnerId,
            request.firmJobNumber(),
            batch.getQuantity(),
            dateOrToday(request.sentOn()),
            colourId,
            request.note());
    if (priceClass != null) {
      opened.assignPriceClass(priceClass.id());
    }
    BatchFinishingStep step = stepRepository.save(opened);
    batch.transitionStatus(BatchStatus.ON_HOLD, TenantContext.getCurrentUserId());
    batchRepository.save(batch);

    events.publishEvent(
        new BatchFinishingSentEvent(
            tenantId,
            batchId,
            step.getId(),
            process.id(),
            partnerId,
            step.getFirmJobNumber(),
            step.getSentMetres()));
    return toDto(step, batch, process, priceClass, List.of());
  }

  /** The lot is back: records what returned, releases the lot and applies the step's colour. */
  @Transactional
  public BatchFinishingDtos.StepDto receive(
      UUID stepId, BatchFinishingDtos.ReceiveRequest request) {
    UUID tenantId = TenantContext.requireTenantId();
    BatchFinishingStep step = step(tenantId, stepId);
    Batch batch = lockedFabricLot(tenantId, step.getBatchId());
    FinishingGradeBreakdown breakdown =
        stepGrades.checked(request.grades(), request.returnedMetres());

    step.receive(
        request.returnedMetres(),
        dateOrToday(request.returnedOn()),
        request.firmJobNumber(),
        request.costAmount(),
        request.costCurrency(),
        request.note());
    stepRepository.save(step);
    stepGrades.save(tenantId, step.getId(), breakdown);
    applyLatestColour(tenantId, batch);
    releaseHold(batch);
    batchRepository.save(batch);

    events.publishEvent(
        new BatchFinishingReceivedEvent(
            tenantId,
            batch.getId(),
            step.getId(),
            step.getProcessId(),
            step.getPartnerId(),
            step.getFirmJobNumber(),
            step.getSentMetres(),
            step.getReturnedMetres(),
            step.getColourId(),
            step.getCostAmount(),
            step.getCostCurrency()));
    return toDto(
        step,
        batch,
        processes(Set.of(step.getProcessId())).get(step.getProcessId()),
        priceClassOf(priceClasses(List.of(step)), step),
        stepGrades.forSteps(tenantId, Set.of(step.getId())).getOrDefault(step.getId(), List.of()));
  }

  /** Withdraws a step that was entered by mistake while the lot never left. */
  @Transactional
  public BatchFinishingDtos.StepDto cancel(UUID stepId, BatchFinishingDtos.CancelRequest request) {
    UUID tenantId = TenantContext.requireTenantId();
    BatchFinishingStep step = step(tenantId, stepId);
    Batch batch = lockedFabricLot(tenantId, step.getBatchId());
    step.cancel(request.reason());
    stepRepository.save(step);
    releaseHold(batch);
    batchRepository.save(batch);
    return toDto(
        step,
        batch,
        processes(Set.of(step.getProcessId())).get(step.getProcessId()),
        priceClassOf(priceClasses(List.of(step)), step),
        List.of());
  }

  /**
   * Enters a step that was finished before the lot came to us, so a bought dyed lot opens with the
   * history it arrived with. It sends nothing anywhere and publishes nothing: what the lot cost is
   * already in what we paid for it.
   */
  @Transactional
  public BatchFinishingDtos.StepDto recordDone(
      UUID batchId, BatchFinishingDtos.RecordDoneRequest request) {
    UUID tenantId = TenantContext.requireTenantId();
    Batch batch = lockedFabricLot(tenantId, batchId);
    if (stepRepository.existsByTenantIdAndBatchIdAndStatusAndIsActiveTrue(
        tenantId, batchId, BatchFinishingStepStatus.SENT)) {
      throw new BatchDomainException(
          "Lot " + batch.getBatchCode() + " is out for a step; receive it before adding history",
          "FINISHING_LOT_IS_OUT",
          422);
    }
    FabricFinishingProcessDto process = usableProcess(request.processId());
    BatchFinishingStep step =
        stepRepository.save(
            BatchFinishingStep.recordDone(
                tenantId,
                batchId,
                nextSequence(tenantId, batchId),
                process.id(),
                activePartner(tenantId, request.partnerId()),
                request.firmJobNumber(),
                request.sentMetres(),
                request.sentOn(),
                request.returnedMetres(),
                request.returnedOn(),
                colourFor(process, request.colorId()),
                request.costAmount(),
                request.costCurrency(),
                request.note()));
    applyLatestColour(tenantId, batch);
    batchRepository.save(batch);
    return toDto(step, batch, process, null, List.of());
  }

  @Transactional(readOnly = true)
  public BatchFinishingDtos.HistoryDto history(UUID batchId) {
    UUID tenantId = TenantContext.requireTenantId();
    Batch batch = fabricLot(tenantId, batchId);
    List<BatchFinishingStep> steps =
        stepRepository.findByTenantIdAndBatchIdAndIsActiveTrueOrderBySequenceAsc(tenantId, batchId);
    Map<UUID, FabricFinishingProcessDto> processes =
        processes(steps.stream().map(BatchFinishingStep::getProcessId).collect(Collectors.toSet()));
    Map<UUID, FabricFinishingPriceClassDto> classes = priceClasses(steps);
    Map<UUID, List<BatchFinishingDtos.StepGradeDto>> grades = gradesOf(tenantId, steps);
    List<BatchFinishingDtos.StepDto> dtos =
        steps.stream()
            .map(
                step ->
                    toDto(
                        step,
                        batch,
                        processes.get(step.getProcessId()),
                        priceClassOf(classes, step),
                        grades.getOrDefault(step.getId(), List.of())))
            .toList();
    Set<FabricFinishingCategory> applied = new LinkedHashSet<>();
    for (BatchFinishingDtos.StepDto dto : dtos) {
      if (dto.status() == BatchFinishingStepStatus.DONE) {
        applied.add(dto.category());
      }
    }
    return new BatchFinishingDtos.HistoryDto(
        batchId, steps.stream().anyMatch(BatchFinishingStep::isOut), List.copyOf(applied), dtos);
  }

  /** Steps in a status across lots, newest first; with SENT it is what is out at the firms. */
  @Transactional(readOnly = true)
  public List<BatchFinishingDtos.StepDto> search(
      BatchFinishingStepStatus status, UUID partnerId, String jobNumber) {
    UUID tenantId = TenantContext.requireTenantId();
    List<BatchFinishingStep> steps =
        stepRepository.search(
            tenantId,
            status == null ? BatchFinishingStepStatus.SENT : status,
            partnerId,
            jobPattern(jobNumber),
            PageRequest.of(0, OPEN_LIST_LIMIT));
    if (steps.isEmpty()) {
      return List.of();
    }
    Map<UUID, Batch> batches =
        batchRepository
            .findByTenantIdAndIdInAndIsActiveTrue(
                tenantId,
                steps.stream().map(BatchFinishingStep::getBatchId).collect(Collectors.toSet()))
            .stream()
            .collect(Collectors.toMap(Batch::getId, Function.identity()));
    Map<UUID, FabricFinishingProcessDto> processes =
        processes(steps.stream().map(BatchFinishingStep::getProcessId).collect(Collectors.toSet()));
    Map<UUID, FabricFinishingPriceClassDto> classes = priceClasses(steps);
    Map<UUID, List<BatchFinishingDtos.StepGradeDto>> grades = gradesOf(tenantId, steps);
    return steps.stream()
        .filter(step -> batches.containsKey(step.getBatchId()))
        .map(
            step ->
                toDto(
                    step,
                    batches.get(step.getBatchId()),
                    processes.get(step.getProcessId()),
                    priceClassOf(classes, step),
                    grades.getOrDefault(step.getId(), List.of())))
        .toList();
  }

  /**
   * The finishing prices that apply to a lot being sent for a process: the firm's own tariff when
   * it has one that fits, else the general prices, one per quality grade the firm prices
   * separately. The lot's formation and weights come from its fabric article; the price class is
   * the tier the firm puts the colour in, which the caller knows. No amount is computed here: the
   * metres that come back are only known when the lot returns, so the caller multiplies.
   */
  @Transactional(readOnly = true)
  public List<FabricFinishingPriceDto> prices(
      UUID batchId, UUID processId, UUID partnerId, UUID priceClassId, LocalDate date) {
    UUID tenantId = TenantContext.requireTenantId();
    Batch batch = fabricLot(tenantId, batchId);
    FabricArticleDto article = fabrics.findArticleByProduct(batch.getProductId()).orElse(null);
    return fabrics.findFinishingPrices(
        new FinishingPriceQuery(
            processId,
            partnerId,
            dateOrToday(date),
            priceClassId,
            article == null ? null : article.formation(),
            article == null ? null : article.gsmGreige(),
            article == null ? null : article.gsmFinished()));
  }

  // ── Rules ──────────────────────────────────────────────────────────────────

  /** Only a free lot goes out whole; a lot with commitments must be sorted out first. */
  private void requireLotMayLeave(UUID tenantId, Batch batch) {
    if (!"M".equalsIgnoreCase(batch.getUnit())) {
      throw new BatchDomainException(
          "Finishing counts metres; lot "
              + batch.getBatchCode()
              + " is counted in "
              + batch.getUnit(),
          "FINISHING_LOT_UNIT_NOT_METRES",
          422);
    }
    if (batch.getStatus() != BatchStatus.AVAILABLE) {
      throw new BatchDomainException(
          "Lot "
              + batch.getBatchCode()
              + " is "
              + batch.getStatus()
              + "; only an available lot can go out",
          "FINISHING_LOT_NOT_AVAILABLE",
          422);
    }
    if (batch.getReservedQuantity().signum() > 0 || batch.getConsumedQuantity().signum() > 0) {
      throw new BatchDomainException(
          "Lot "
              + batch.getBatchCode()
              + " has reserved or consumed metres and cannot go out whole",
          "FINISHING_LOT_COMMITTED",
          422);
    }
    if (stockUnitRepository.countByTenantIdAndBatchIdAndIsActiveTrueAndStatusIn(
            tenantId, batch.getId(), PIECE_STATUSES_THAT_MUST_STAY)
        > 0) {
      throw new BatchDomainException(
          "Lot " + batch.getBatchCode() + " has reserved or moving rolls and cannot go out whole",
          "FINISHING_LOT_PIECES_COMMITTED",
          422);
    }
  }

  private UUID colourFor(FabricFinishingProcessDto process, UUID colorId) {
    if (!process.colours()) {
      if (colorId != null) {
        throw new BatchDomainException(
            process.name() + " does not give a colour", "FINISHING_COLOUR_NOT_ALLOWED", 422);
      }
      return null;
    }
    if (colorId == null) {
      throw new BatchDomainException(
          process.name() + " needs the colour it gives", "FINISHING_COLOUR_REQUIRED", 422);
    }
    return colours
        .findActiveReferenceById(colorId)
        .map(ColorQueryService.ColorReference::id)
        .orElseThrow(() -> new NotFoundException("Active color not found: " + colorId));
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

  private FabricFinishingPriceClassDto usablePriceClass(UUID priceClassId) {
    if (priceClassId == null) {
      return null;
    }
    return fabrics
        .findUsablePriceClass(priceClassId)
        .orElseThrow(
            () -> new NotFoundException("Finishing price class not found: " + priceClassId));
  }

  private FabricFinishingProcessDto usableProcess(UUID processId) {
    return fabrics
        .findUsableFinishingProcess(processId)
        .orElseThrow(() -> new NotFoundException("Finishing process not found: " + processId));
  }

  /**
   * The lot's colour is that of its latest done colour-giving step, so an over-dye replaces the
   * colour while the earlier one stays readable on its own step.
   */
  private void applyLatestColour(UUID tenantId, Batch batch) {
    stepRepository
        .findByTenantIdAndBatchIdAndIsActiveTrueOrderBySequenceAsc(tenantId, batch.getId())
        .stream()
        .filter(step -> step.isDone() && step.getColourId() != null)
        .max(
            Comparator.comparing(BatchFinishingStep::getReturnedOn)
                .thenComparing(BatchFinishingStep::getSequence))
        .ifPresent(step -> batch.assignColor(step.getColourId()));
  }

  /** Gives the lot back to stock if this service is what held it. */
  private void releaseHold(Batch batch) {
    if (batch.getStatus() == BatchStatus.ON_HOLD) {
      batch.transitionStatus(BatchStatus.AVAILABLE, TenantContext.getCurrentUserId());
    }
  }

  private int nextSequence(UUID tenantId, UUID batchId) {
    return stepRepository.lastSequence(tenantId, batchId) + 1;
  }

  private LocalDate dateOrToday(LocalDate date) {
    return date != null ? date : LocalDate.now(clock);
  }

  private static String jobPattern(String jobNumber) {
    if (jobNumber == null || jobNumber.isBlank()) {
      return "%";
    }
    String escaped =
        jobNumber
            .trim()
            .toLowerCase(Locale.ROOT)
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_");
    return "%" + escaped + "%";
  }

  // ── Loading ────────────────────────────────────────────────────────────────

  private Batch lockedFabricLot(UUID tenantId, UUID batchId) {
    Batch batch =
        batchRepository
            .findByIdAndTenantIdForUpdate(batchId, tenantId)
            .filter(found -> Boolean.TRUE.equals(found.getIsActive()))
            .orElseThrow(() -> new NotFoundException("Lot not found: " + batchId));
    return requireFabric(batch);
  }

  private Batch fabricLot(UUID tenantId, UUID batchId) {
    Batch batch =
        batchRepository
            .findByIdAndTenantId(batchId, tenantId)
            .filter(found -> Boolean.TRUE.equals(found.getIsActive()))
            .orElseThrow(() -> new NotFoundException("Lot not found: " + batchId));
    return requireFabric(batch);
  }

  private static Batch requireFabric(Batch batch) {
    if (batch.getProductType() != ProductType.FABRIC) {
      throw new BatchDomainException(
          "Finishing steps are recorded on fabric lots only", "FINISHING_LOT_NOT_FABRIC", 422);
    }
    return batch;
  }

  private BatchFinishingStep step(UUID tenantId, UUID stepId) {
    return stepRepository
        .findByTenantIdAndIdAndIsActiveTrue(tenantId, stepId)
        .orElseThrow(() -> new NotFoundException("Finishing step not found: " + stepId));
  }

  private Map<UUID, FabricFinishingProcessDto> processes(Set<UUID> ids) {
    return fabrics.findFinishingProcesses(ids);
  }

  private Map<UUID, FabricFinishingPriceClassDto> priceClasses(
      Collection<BatchFinishingStep> steps) {
    return fabrics.findPriceClasses(
        steps.stream()
            .map(BatchFinishingStep::getPriceClassId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet()));
  }

  /** The grade splits of the steps; only a received step can have one. */
  private Map<UUID, List<BatchFinishingDtos.StepGradeDto>> gradesOf(
      UUID tenantId, Collection<BatchFinishingStep> steps) {
    return stepGrades.forSteps(
        tenantId,
        steps.stream().filter(BatchFinishingStep::isDone).map(BatchFinishingStep::getId).toList());
  }

  /** The step's price class, if it has one; an empty map must not be asked for a null key. */
  private static FabricFinishingPriceClassDto priceClassOf(
      Map<UUID, FabricFinishingPriceClassDto> classes, BatchFinishingStep step) {
    return step.getPriceClassId() == null ? null : classes.get(step.getPriceClassId());
  }

  private static BatchFinishingDtos.StepDto toDto(
      BatchFinishingStep step,
      Batch batch,
      FabricFinishingProcessDto process,
      FabricFinishingPriceClassDto priceClass,
      List<BatchFinishingDtos.StepGradeDto> grades) {
    return new BatchFinishingDtos.StepDto(
        step.getId(),
        step.getBatchId(),
        batch.getBatchCode(),
        batch.getProductId(),
        step.getSequence(),
        step.getProcessId(),
        process.code(),
        process.name(),
        process.category(),
        step.getPartnerId(),
        step.getFirmJobNumber(),
        step.getStatus(),
        step.getSentMetres(),
        step.getSentOn(),
        step.getReturnedMetres(),
        step.getReturnedOn(),
        step.lossMetres(),
        grades,
        step.getColourId(),
        step.getPriceClassId(),
        priceClass == null ? null : priceClass.code(),
        priceClass == null ? null : priceClass.name(),
        step.getCostAmount(),
        step.getCostCurrency(),
        step.getNote(),
        step.getCreatedAt());
  }
}
