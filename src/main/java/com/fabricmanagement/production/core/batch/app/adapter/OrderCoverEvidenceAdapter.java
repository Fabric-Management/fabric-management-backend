package com.fabricmanagement.production.core.batch.app.adapter;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.serialization.CanonicalJsonFingerprint;
import com.fabricmanagement.product.core.app.ProductEvidenceQueryService;
import com.fabricmanagement.product.core.domain.ProductType;
import com.fabricmanagement.product.qualitygrade.api.query.QualityGradeQueryService;
import com.fabricmanagement.production.core.batch.app.BatchCertificateEvidencePolicy;
import com.fabricmanagement.production.core.batch.app.BatchPrimaryMeasureService;
import com.fabricmanagement.production.core.batch.app.StockAvailabilityQueryService;
import com.fabricmanagement.production.core.batch.domain.*;
import com.fabricmanagement.production.core.batch.domain.attributes.FiberAttributes;
import com.fabricmanagement.production.core.batch.domain.exception.BatchDomainException;
import com.fabricmanagement.production.core.batch.dto.StockAvailabilityDtos;
import com.fabricmanagement.production.core.batch.infra.repository.*;
import com.fabricmanagement.production.core.stockunit.domain.*;
import com.fabricmanagement.production.core.stockunit.infra.repository.StockUnitRepository;
import com.fabricmanagement.sales.salesorder.domain.port.OrderCoverEvidencePort;
import com.fabricmanagement.sales.salesorder.dto.OrderCoverEvidenceDto.Source;
import com.fabricmanagement.sales.salesorder.dto.OrderCoverEvidenceDto.SourceKnowledge;
import jakarta.persistence.EntityManager;
import jakarta.persistence.OptimisticLockException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * All physical arithmetic delegates to production's existing canonical availability calculation.
 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderCoverEvidenceAdapter implements OrderCoverEvidencePort {
  // Stable identity of the code-defined conversion authority, not a fabricated database record.
  private static final Source PRIMARY_MEASURE_RULE =
      new Source(
          "PRIMARY_MEASURE_RULE",
          UUID.nameUUIDFromBytes(
              "BatchPrimaryMeasureService:V1".getBytes(java.nio.charset.StandardCharsets.UTF_8)),
          1L,
          null,
          null,
          SourceKnowledge.VERIFIED,
          null);
  private final StockAvailabilityQueryService availability;
  private final BatchPrimaryMeasureService measures;
  private final BatchRepository batches;
  private final StockUnitRepository units;
  private final BatchLotQuantityIntentRepository intents;
  private final BatchReservationRepository reservations;
  private final BatchCertificationRepository certifications;
  private final List<BatchCertificateEvidencePolicy> certificatePolicies;
  private final QualityGradeQueryService grades;
  private final ProductEvidenceQueryService products;
  private final EntityManager entityManager;
  private final Clock clock;

  @Override
  public Inputs inspect(Requirements requirements) {
    return inspect(requirements, null);
  }

  private Inputs inspect(Requirements requirements, LockedRows locked) {
    requireTenant(requirements);
    LocalDate certificateEvaluationDate = LocalDate.now(clock);
    if (requirements.productIds().isEmpty())
      return new Inputs(
          requirements.lines().stream()
              .map(
                  line ->
                      new Demand(line.lineId(), null, line.unit(), "PRODUCT_REQUIREMENT_MISSING"))
              .toList(),
          List.of());
    List<Batch> population =
        batches.findByTenantIdAndProductIdInAndIsActiveTrueOrderById(
            requirements.tenantId(), requirements.productIds());
    var productReferences =
        products.findReferences(requirements.productIds()).stream()
            .collect(
                Collectors.toMap(ProductEvidenceQueryService.Reference::id, Function.identity()));
    Map<UUID, StockAvailabilityDtos.Lot> availabilityLots =
        availability.lotsForProducts(requirements.productIds()).stream()
            .collect(Collectors.toMap(StockAvailabilityDtos.Lot::batchId, Function.identity()));
    List<UUID> ids = population.stream().map(Batch::getId).toList();
    List<StockUnit> stockUnits =
        ids.isEmpty()
            ? List.of()
            : units.findByTenantIdAndBatchIdInAndIsActiveTrue(requirements.tenantId(), ids);
    List<BatchLotQuantityIntent> lotIntents =
        ids.isEmpty()
            ? List.of()
            : intents.findByTenantIdAndBatchIdInAndIsActiveTrueOrderById(
                requirements.tenantId(), ids);
    List<BatchReservation> lotReservations =
        ids.isEmpty()
            ? List.of()
            : reservations.findByTenantIdAndBatchIdInAndIsActiveTrueOrderById(
                requirements.tenantId(), ids);
    List<BatchCertification> lotCertifications =
        ids.isEmpty()
            ? List.of()
            : certifications.findEvidenceByTenantAndBatchIds(requirements.tenantId(), ids);
    if (locked != null) {
      requireVersions(population, locked.batches());
      requireVersions(stockUnits, locked.units());
      requireVersions(lotIntents, locked.intents());
      requireVersions(lotReservations, locked.reservations());
      requireVersions(lotCertifications, locked.certifications());
    }
    var gradeEvidence =
        grades
            .findEvidenceReferencesByIds(
                stockUnits.stream()
                    .map(StockUnit::getQualityGradeId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet()))
            .stream()
            .collect(Collectors.toMap(reference -> reference.grade().id(), Function.identity()));
    var gradeReferences =
        gradeEvidence.values().stream()
            .map(QualityGradeQueryService.QualityGradeEvidenceReference::grade)
            .collect(
                Collectors.toMap(
                    QualityGradeQueryService.QualityGradeReference::id, Function.identity()));
    var unitsByBatch = stockUnits.stream().collect(Collectors.groupingBy(StockUnit::getBatchId));
    var intentsByBatch =
        lotIntents.stream().collect(Collectors.groupingBy(BatchLotQuantityIntent::getBatchId));
    var reservationsByBatch =
        lotReservations.stream().collect(Collectors.groupingBy(BatchReservation::getBatchId));
    var certificationsByBatch =
        lotCertifications.stream()
            .collect(Collectors.groupingBy(certification -> certification.getBatch().getId()));

    List<Lot> result =
        population.stream()
            .map(
                batch -> {
                  var pieces = unitsByBatch.getOrDefault(batch.getId(), List.of());
                  List<SourceFact> facts = new ArrayList<>();
                  facts.add(
                      new SourceFact(
                          PRIMARY_MEASURE_RULE,
                          "BatchPrimaryMeasureService:V1",
                          null,
                          null,
                          null,
                          null,
                          null,
                          null,
                          null));
                  facts.add(
                      new SourceFact(
                          source("BATCH", batch),
                          batch.getStatus().name(),
                          batch.getQuantity(),
                          batch.getUnit(),
                          batch.getConsumedQuantity(),
                          batch.getUnit(),
                          batch.getProductId(),
                          null,
                          null));
                  pieces.forEach(
                      piece ->
                          facts.add(
                              new SourceFact(
                                  source("STOCK_UNIT", piece),
                                  piece.getStatus().name() + ":" + piece.getQualityDisposition(),
                                  piece.getCurrentWeight(),
                                  piece.getUnit(),
                                  piece.getLength(),
                                  piece.getLengthUnit(),
                                  piece.getQualityGradeId(),
                                  null,
                                  null)));
                  intentsByBatch
                      .getOrDefault(batch.getId(), List.of())
                      .forEach(
                          intent ->
                              facts.add(
                                  new SourceFact(
                                      source("LOT_INTENT", intent),
                                      intent.getStatus().name(),
                                      intent.getQuantity(),
                                      intent.getUnit(),
                                      null,
                                      null,
                                      intent.getQuoteLineId(),
                                      null,
                                      null)));
                  reservationsByBatch.getOrDefault(batch.getId(), List.of()).stream()
                      .forEach(
                          reservation ->
                              facts.add(
                                  new SourceFact(
                                      source("BATCH_RESERVATION", reservation),
                                      reservation.getStatus().name(),
                                      reservation.getReservedQuantity(),
                                      reservation.getUnit(),
                                      reservation.getConsumedQuantity(),
                                      reservation.getUnit(),
                                      reservation.getReferenceId(),
                                      null,
                                      null)));
                  certificationsByBatch
                      .getOrDefault(batch.getId(), List.of())
                      .forEach(
                          certification ->
                              facts.add(
                                  new SourceFact(
                                      source("BATCH_CERTIFICATION", certification),
                                      certificationState(certification),
                                      null,
                                      null,
                                      null,
                                      null,
                                      certification.getCertification().getId(),
                                      null,
                                      null)));
                  certificatePolicies.stream()
                      .sorted(Comparator.comparing(BatchCertificateEvidencePolicy::policyId))
                      .forEach(
                          policy ->
                              facts.add(
                                  new SourceFact(
                                      certificatePolicySource(policy),
                                      policy.policyId()
                                          + ":"
                                          + policy.policyVersion()
                                          + ":"
                                          + certificateEvaluationDate,
                                      null,
                                      null,
                                      null,
                                      null,
                                      null,
                                      null,
                                      null)));
                  pieces.stream()
                      .map(StockUnit::getQualityGradeId)
                      .filter(Objects::nonNull)
                      .distinct()
                      .sorted(Comparator.comparing(UUID::toString))
                      .forEach(
                          gradeId -> {
                            var grade = gradeReferences.get(gradeId);
                            var provenance = gradeEvidence.get(gradeId);
                            facts.add(
                                new SourceFact(
                                    new Source(
                                        "QUALITY_GRADE",
                                        gradeId,
                                        provenance == null ? null : provenance.revision(),
                                        null,
                                        provenance == null ? null : provenance.recordedAt(),
                                        provenance == null
                                            ? SourceKnowledge.UNKNOWN
                                            : SourceKnowledge.VERIFIED,
                                        null),
                                    grade == null
                                        ? "MISSING"
                                        : grade.active() ? "ACTIVE" : "ARCHIVED",
                                    null,
                                    null,
                                    null,
                                    null,
                                    null,
                                    grade == null ? null : grade.saleable(),
                                    grade == null ? null : grade.rank()));
                          });
                  facts.sort(
                      Comparator.comparing((SourceFact fact) -> fact.source().type())
                          .thenComparing(fact -> fact.source().id().toString()));
                  Lot base =
                      classify(
                          batch,
                          availabilityLots.get(batch.getId()),
                          pieces,
                          facts,
                          gradeReferences,
                          productReferences.get(batch.getProductId()));
                  return withLineSuitabilities(
                      batch,
                      base,
                      requirements.lines(),
                      certificationsByBatch.getOrDefault(batch.getId(), List.of()),
                      certificateEvaluationDate);
                })
            .toList();

    List<Demand> demands =
        requirements.lines().stream()
            .map(
                line -> {
                  var product = productReferences.get(line.productId());
                  List<Source> sources =
                      product == null
                          ? List.of(PRIMARY_MEASURE_RULE)
                          : List.of(
                              PRIMARY_MEASURE_RULE,
                              new Source(
                                  "PRODUCT",
                                  product.id(),
                                  product.revision(),
                                  null,
                                  product.recordedAt(),
                                  SourceKnowledge.VERIFIED,
                                  null));
                  if (product == null || !product.active())
                    return new Demand(
                        line.lineId(), null, line.unit(), "PRODUCT_REFERENCE_UNKNOWN", sources);
                  var resolution = measures.findResolution(product.productType());
                  if (resolution.isEmpty())
                    return new Demand(
                        line.lineId(), null, line.unit(), "PRIMARY_MEASURE_UNKNOWN", sources);
                  var measure = resolution.orElseThrow();
                  var quantity =
                      measures.toCanonical(line.requested(), line.unit(), measure.primaryMeasure());
                  return new Demand(
                      line.lineId(),
                      quantity.orElse(null),
                      measure.primaryUnit(),
                      quantity.isEmpty() ? "UNSUPPORTED_UNIT_CONVERSION" : null,
                      sources);
                })
            .toList();
    return new Inputs(demands, result);
  }

  private Lot classify(
      Batch batch,
      StockAvailabilityDtos.Lot row,
      List<StockUnit> pieces,
      List<SourceFact> facts,
      Map<UUID, QualityGradeQueryService.QualityGradeReference> gradeReferences,
      ProductEvidenceQueryService.Reference product) {
    LinkedHashSet<String> reasons = new LinkedHashSet<>();
    BigDecimal physical = row == null ? null : row.free();
    BigDecimal suitable = null;
    Eligibility eligibility = Eligibility.UNKNOWN;
    if (product == null || !product.active()) reasons.add("PRODUCT_REFERENCE_UNKNOWN");
    else if (batch.getProductType() != product.productType()) reasons.add("PRODUCT_TYPE_CONFLICT");
    else if (row == null) reasons.add("UNSUPPORTED_PRODUCT_MEASURE");
    else if (BatchStatus.BLOCKED_FOR_RELEASED_UNIT_CONSUMPTION.contains(batch.getStatus())
        || pieces.isEmpty() && BatchStatus.BLOCKED_FOR_PRODUCTION.contains(batch.getStatus())) {
      eligibility = Eligibility.EXCLUDED;
      suitable = BigDecimal.ZERO;
      reasons.add("BATCH_" + batch.getStatus().name());
    } else {
      pieces.stream()
          .filter(
              piece ->
                  piece.getQualityDisposition() != QualityDisposition.RELEASED
                      || !Set.of(StockUnitStatus.AVAILABLE, StockUnitStatus.PARTIAL)
                          .contains(piece.getStatus()))
          .forEach(
              piece ->
                  reasons.add(
                      "STOCK_UNIT_" + piece.getStatus() + "_" + piece.getQualityDisposition()));
      boolean hasUnknownGrade =
          pieces.stream()
              .filter(
                  piece ->
                      piece.getQualityDisposition() == QualityDisposition.RELEASED
                          && Set.of(StockUnitStatus.AVAILABLE, StockUnitStatus.PARTIAL)
                              .contains(piece.getStatus()))
              .anyMatch(
                  piece ->
                      piece.getQualityGradeId() == null
                          || !gradeReferences.containsKey(piece.getQualityGradeId()));
      var breakdown = row.qualityBreakdown();
      hasUnknownGrade |=
          breakdown.stream()
              .anyMatch(
                  grade ->
                      grade.grade() == null || !gradeReferences.containsKey(grade.grade().id()));
      boolean gradeConflict =
          breakdown.stream()
              .filter(grade -> grade.grade() != null)
              .anyMatch(
                  grade -> {
                    var reference = gradeReferences.get(grade.grade().id());
                    return reference != null
                        && (reference.saleable() != grade.grade().saleable()
                            || reference.rank() != grade.grade().rank());
                  });
      if (gradeConflict) reasons.add("QUALITY_GRADE_SOURCE_CHANGED");
      boolean nonSaleable =
          breakdown.stream()
              .anyMatch(
                  grade ->
                      grade.grade() != null
                          && gradeReferences.containsKey(grade.grade().id())
                          && (!grade.grade().saleable()
                              || !gradeReferences.get(grade.grade().id()).active()));
      boolean saleable =
          breakdown.stream()
              .anyMatch(
                  grade ->
                      grade.grade() != null
                          && gradeReferences.containsKey(grade.grade().id())
                          && grade.grade().saleable()
                          && gradeReferences.get(grade.grade().id()).active());
      if (nonSaleable) reasons.add("NON_SALEABLE_GRADE");
      if (row.overCommitted()) reasons.add("OVER_COMMITTED");
      if (!row.unitMismatches().isEmpty()) reasons.add("UNIT_MISMATCH");
      if (pieces.isEmpty()) reasons.add("BATCH_FALLBACK_QUALITY_UNKNOWN");
      if (hasUnknownGrade) reasons.add("QUALITY_GRADE_UNKNOWN");
      boolean unknown =
          pieces.isEmpty() || hasUnknownGrade || gradeConflict || !row.unitMismatches().isEmpty();
      boolean missingPrimary =
          pieces.stream()
              .filter(
                  piece ->
                      piece.getQualityDisposition() == QualityDisposition.RELEASED
                          && Set.of(StockUnitStatus.AVAILABLE, StockUnitStatus.PARTIAL)
                              .contains(piece.getStatus()))
              .anyMatch(
                  piece ->
                      measures
                          .toCanonical(
                              row.primaryMeasure() == PrimaryMeasure.WEIGHT
                                  ? piece.getCurrentWeight()
                                  : piece.getLength(),
                              row.primaryMeasure() == PrimaryMeasure.WEIGHT
                                  ? piece.getUnit()
                                  : piece.getLengthUnit(),
                              row.primaryMeasure())
                          .isEmpty());
      if (missingPrimary) {
        unknown = true;
        reasons.add("PRIMARY_MEASUREMENT_UNKNOWN");
      }
      if (!unknown
          && nonSaleable
          && saleable
          && row.softIntent().add(row.hardReserved()).signum() > 0) {
        unknown = true;
        reasons.add("COMMITMENT_GRADE_SCOPE_UNKNOWN");
      }
      if (!unknown) {
        BigDecimal eligiblePhysical =
            breakdown.stream()
                .filter(
                    grade ->
                        grade.grade() != null
                            && gradeReferences.containsKey(grade.grade().id())
                            && grade.grade().saleable()
                            && gradeReferences.get(grade.grade().id()).active())
                .map(
                    grade ->
                        row.primaryMeasure() == PrimaryMeasure.WEIGHT ? grade.kg() : grade.metres())
                .reduce(BigDecimal.ZERO, (left, right) -> right == null ? left : left.add(right));
        boolean missingMeasure =
            breakdown.stream()
                .filter(grade -> grade.grade() != null && grade.grade().saleable())
                .anyMatch(
                    grade ->
                        (row.primaryMeasure() == PrimaryMeasure.WEIGHT
                                ? grade.kg()
                                : grade.metres())
                            == null);
        if (missingMeasure) reasons.add("PRIMARY_MEASUREMENT_UNKNOWN");
        else {
          // For homogeneous eligible stock reuse free; for an uncommitted mixed lot use its
          // measured
          // eligible subset. Never allocate unscoped commitments to a grade subset.
          suitable = nonSaleable ? eligiblePhysical : row.free();
          eligibility = suitable.signum() == 0 ? Eligibility.EXCLUDED : Eligibility.ELIGIBLE;
        }
      }
    }
    if (row == null
        || !row.unitMismatches().isEmpty()
        || reasons.contains("PRIMARY_MEASUREMENT_UNKNOWN")) physical = null;
    List<Source> sources = facts.stream().map(SourceFact::source).toList();
    List<Comparison> comparisons =
        List.of(
            new Comparison(
                "PRODUCT_IDENTITY",
                reasons.contains("PRODUCT_REFERENCE_UNKNOWN")
                        || reasons.contains("PRODUCT_TYPE_CONFLICT")
                    ? ComparisonResult.UNKNOWN
                    : ComparisonResult.MATCH,
                sources.stream().filter(source -> source.type().equals("BATCH")).toList()),
            new Comparison(
                "PRIMARY_MEASURE",
                row == null
                        || reasons.contains("PRODUCT_REFERENCE_UNKNOWN")
                        || reasons.contains("PRODUCT_TYPE_CONFLICT")
                        || reasons.contains("UNIT_MISMATCH")
                        || reasons.contains("PRIMARY_MEASUREMENT_UNKNOWN")
                    ? ComparisonResult.UNKNOWN
                    : ComparisonResult.MATCH,
                sources),
            new Comparison(
                "QUALITY_SALEABILITY",
                row == null
                        || reasons.contains("BATCH_FALLBACK_QUALITY_UNKNOWN")
                        || reasons.contains("QUALITY_GRADE_UNKNOWN")
                        || reasons.contains("QUALITY_GRADE_SOURCE_CHANGED")
                    ? ComparisonResult.UNKNOWN
                    : eligibility == Eligibility.EXCLUDED
                        ? ComparisonResult.EXCLUDED
                        : eligibility == Eligibility.UNKNOWN
                            ? ComparisonResult.UNKNOWN
                            : ComparisonResult.MATCH,
                sources),
            new Comparison(
                "COMMITMENT_SCOPE",
                row == null
                        || reasons.contains("UNIT_MISMATCH")
                        || reasons.contains("COMMITMENT_GRADE_SCOPE_UNKNOWN")
                    ? ComparisonResult.UNKNOWN
                    : ComparisonResult.MATCH,
                sources));
    return new Lot(
        batch.getId(),
        batch.getProductId(),
        row == null ? null : row.primaryMeasureUnit(),
        physical,
        suitable,
        eligibility,
        List.copyOf(reasons),
        sources,
        facts,
        comparisons,
        CanonicalJsonFingerprint.of(facts, Set.of("observedAt")));
  }

  private Lot withLineSuitabilities(
      Batch batch,
      Lot base,
      List<OrderCoverEvidencePort.Requirement> requirements,
      List<BatchCertification> batchCertifications,
      LocalDate certificateEvaluationDate) {
    List<LineSuitability> assessments =
        requirements.stream()
            .filter(requirement -> Objects.equals(requirement.productId(), batch.getProductId()))
            .map(
                requirement ->
                    assessForLine(
                        batch, base, requirement, batchCertifications, certificateEvaluationDate))
            .toList();
    return new Lot(
        base.lotId(),
        base.productId(),
        base.unit(),
        base.physicalFree(),
        base.suitableFree(),
        base.eligibility(),
        base.reasons(),
        base.sources(),
        base.facts(),
        base.comparisons(),
        assessments,
        base.sourceFingerprint());
  }

  private LineSuitability assessForLine(
      Batch batch,
      Lot base,
      OrderCoverEvidencePort.Requirement requirement,
      List<BatchCertification> batchCertifications,
      LocalDate certificateEvaluationDate) {
    if (requirement.profile() == null) {
      return new LineSuitability(
          requirement.lineId(), base.eligibility(), base.reasons(), base.comparisons());
    }
    if (base.eligibility() == Eligibility.EXCLUDED) {
      return new LineSuitability(
          requirement.lineId(), base.eligibility(), base.reasons(), base.comparisons());
    }

    List<Comparison> comparisons = new ArrayList<>(base.comparisons());
    LinkedHashSet<String> reasons = new LinkedHashSet<>(base.reasons());
    requirement
        .profile()
        .facets()
        .forEach(
            facet ->
                comparisons.add(
                    compareFacet(
                        batch,
                        base,
                        facet,
                        batchCertifications,
                        certificateEvaluationDate,
                        reasons)));
    requirement
        .profile()
        .unsupportedConstraints()
        .forEach(
            constraint -> {
              reasons.add("UNMODELLED_REQUIREMENT:" + constraint);
              comparisons.add(
                  new Comparison(
                      "UNMODELLED_SPEC:" + constraint, ComparisonResult.UNKNOWN, List.of()));
            });

    Eligibility result =
        comparisons.stream().anyMatch(row -> row.result() == ComparisonResult.EXCLUDED)
            ? Eligibility.EXCLUDED
            : comparisons.stream().anyMatch(row -> row.result() == ComparisonResult.UNKNOWN)
                ? Eligibility.UNKNOWN
                : Eligibility.ELIGIBLE;
    return new LineSuitability(
        requirement.lineId(), result, List.copyOf(reasons), List.copyOf(comparisons));
  }

  private Comparison compareFacet(
      Batch batch,
      Lot base,
      Facet facet,
      List<BatchCertification> batchCertifications,
      LocalDate certificateEvaluationDate,
      Set<String> reasons) {
    String dimension = "REQUIREMENT_" + facet.identity();
    if (facet.state() == FacetState.UNCONSTRAINED) {
      return new Comparison(dimension, ComparisonResult.MATCH, List.of());
    }
    if (facet.state() == FacetState.UNSPECIFIED) {
      reasons.add("REQUIREMENT_UNSPECIFIED:" + facet.identity());
      return new Comparison(dimension, ComparisonResult.UNKNOWN, List.of());
    }
    if (facet.kind() == FacetKind.COLOUR_IDENTITY) {
      UUID requiredColour = facet.colourId();
      if (requiredColour == null || batch.getColorId() == null) {
        reasons.add("COLOUR_IDENTITY_UNKNOWN");
        return new Comparison(dimension, ComparisonResult.UNKNOWN, batchSources(base));
      }
      if (!requiredColour.equals(batch.getColorId())) {
        reasons.add("COLOUR_IDENTITY_MISMATCH");
        return new Comparison(dimension, ComparisonResult.EXCLUDED, batchSources(base));
      }
      return new Comparison(dimension, ComparisonResult.MATCH, batchSources(base));
    }
    if (facet.kind() == FacetKind.CERTIFICATION) {
      return compareCertification(
          batch,
          facet,
          facet.certificates(),
          batchCertifications,
          certificateEvaluationDate,
          reasons);
    }
    if (facet.kind() == FacetKind.FIBRE_GRADE || facet.kind() == FacetKind.FIBRE_SHADE) {
      return compareFiberAttribute(batch, base, facet, reasons);
    }

    // SALES-REQ-1 deliberately has no accepted lot policy for GOTS and no evidence owner for the
    // remaining bounded facets. Presence of source records must never be promoted to MATCH here.
    reasons.add("FACET_EVIDENCE_UNKNOWN:" + facet.identity());
    return new Comparison(dimension, ComparisonResult.UNKNOWN, List.of());
  }

  private Comparison compareFiberAttribute(
      Batch batch, Lot base, Facet facet, Set<String> reasons) {
    String dimension = "REQUIREMENT_" + facet.identity();
    String reasonPrefix = facet.kind() == FacetKind.FIBRE_GRADE ? "FIBRE_GRADE" : "FIBRE_SHADE";
    if (batch.getProductType() != ProductType.FIBER) {
      reasons.add(reasonPrefix + "_EVIDENCE_NOT_APPLICABLE");
      return new Comparison(dimension, ComparisonResult.UNKNOWN, batchSources(base));
    }
    FiberAttributes attributes = FiberAttributes.from(batch.getAttributes());
    String actual = facet.kind() == FacetKind.FIBRE_GRADE ? attributes.grade() : attributes.shade();
    if (actual == null || actual.isBlank()) {
      reasons.add(reasonPrefix + "_UNKNOWN");
      return new Comparison(dimension, ComparisonResult.UNKNOWN, batchSources(base));
    }
    String normalized = actual.strip().toUpperCase(Locale.ROOT);
    boolean match =
        switch (facet.rule()) {
          case EXACT, ANY, SET_MEMBERSHIP -> facet.categories().contains(normalized);
          case ALL -> facet.categories().size() == 1 && facet.categories().contains(normalized);
          default -> false;
        };
    if (!match) {
      reasons.add(reasonPrefix + "_MISMATCH");
    }
    return new Comparison(
        dimension, match ? ComparisonResult.MATCH : ComparisonResult.EXCLUDED, batchSources(base));
  }

  private Comparison compareCertification(
      Batch batch,
      Facet facet,
      List<CertificateRequirement> required,
      List<BatchCertification> batchCertifications,
      LocalDate certificateEvaluationDate,
      Set<String> reasons) {
    List<CertificateKindResult> results =
        required.stream()
            .map(
                reference ->
                    assessCertificateKind(
                        batch, reference, batchCertifications, certificateEvaluationDate))
            .toList();
    ComparisonResult result;
    if (facet.rule() == FacetRule.ALL) {
      result =
          results.stream().anyMatch(row -> row.result() == ComparisonResult.EXCLUDED)
              ? ComparisonResult.EXCLUDED
              : results.stream().anyMatch(row -> row.result() == ComparisonResult.UNKNOWN)
                  ? ComparisonResult.UNKNOWN
                  : ComparisonResult.MATCH;
    } else {
      result =
          results.stream().anyMatch(row -> row.result() == ComparisonResult.MATCH)
              ? ComparisonResult.MATCH
              : results.stream().anyMatch(row -> row.result() == ComparisonResult.UNKNOWN)
                  ? ComparisonResult.UNKNOWN
                  : ComparisonResult.EXCLUDED;
    }
    if (result == ComparisonResult.UNKNOWN) {
      reasons.add("FACET_EVIDENCE_UNKNOWN:" + facet.identity());
    } else if (result == ComparisonResult.EXCLUDED) {
      reasons.add("CERTIFICATE_REQUIREMENT_MISMATCH:" + facet.identity());
    }
    List<Source> sources =
        results.stream()
            .flatMap(row -> row.sources().stream())
            .distinct()
            .sorted(
                Comparator.comparing(Source::type).thenComparing(source -> source.id().toString()))
            .toList();
    return new Comparison("REQUIREMENT_" + facet.identity(), result, sources);
  }

  private CertificateKindResult assessCertificateKind(
      Batch batch,
      CertificateRequirement required,
      List<BatchCertification> batchCertifications,
      LocalDate certificateEvaluationDate) {
    BatchCertificateKind kind;
    try {
      kind = BatchCertificateKind.valueOf(required.certificateKind());
    } catch (IllegalArgumentException invalidKind) {
      return new CertificateKindResult(ComparisonResult.UNKNOWN, List.of());
    }
    List<BatchCertificateEvidencePolicy> supportedPolicies =
        certificatePolicies.stream()
            .filter(candidate -> candidate.supports(required.scheme(), kind))
            .toList();
    if (supportedPolicies.isEmpty()) {
      return new CertificateKindResult(ComparisonResult.UNKNOWN, List.of());
    }
    if (supportedPolicies.size() > 1) {
      throw new BatchDomainException(
          "More than one certificate evidence policy claims " + required.scheme() + "/" + kind,
          "BATCH_CERTIFICATE_POLICY_AMBIGUOUS",
          500);
    }
    BatchCertificateEvidencePolicy policy = supportedPolicies.getFirst();
    BatchCertificateEvidencePolicy.Assessment assessment =
        policy.assess(
            batch, required.scheme(), kind, batchCertifications, certificateEvaluationDate);
    if (assessment.outcome() != BatchCertificateEvidencePolicy.Outcome.UNKNOWN
        && assessment.evidence().isEmpty()) {
      throw new BatchDomainException(
          "Certificate policy produced a conclusive result without evidence",
          "BATCH_CERTIFICATE_POLICY_INVALID_RESULT",
          500);
    }
    if (assessment.evidence().stream().anyMatch(row -> !batchCertifications.contains(row))) {
      throw new BatchDomainException(
          "Certificate policy returned evidence outside the evaluated lot",
          "BATCH_CERTIFICATE_POLICY_INVALID_EVIDENCE",
          500);
    }
    ComparisonResult result =
        switch (assessment.outcome()) {
          case MATCH -> ComparisonResult.MATCH;
          case EXCLUDED -> ComparisonResult.EXCLUDED;
          case UNKNOWN -> ComparisonResult.UNKNOWN;
        };
    List<Source> sources =
        new ArrayList<>(
            assessment.evidence().stream()
                .sorted(Comparator.comparing(row -> row.getId().toString()))
                .map(certification -> source("BATCH_CERTIFICATION", certification))
                .toList());
    sources.add(certificatePolicySource(policy));
    return new CertificateKindResult(result, List.copyOf(sources));
  }

  private Source certificatePolicySource(BatchCertificateEvidencePolicy policy) {
    return new Source(
        "CERTIFICATE_EVIDENCE_POLICY",
        UUID.nameUUIDFromBytes(
            (policy.policyId() + ":" + policy.policyVersion())
                .getBytes(java.nio.charset.StandardCharsets.UTF_8)),
        null,
        null,
        null,
        SourceKnowledge.VERIFIED,
        null);
  }

  private record CertificateKindResult(ComparisonResult result, List<Source> sources) {}

  private List<Source> batchSources(Lot lot) {
    return lot.sources().stream().filter(source -> "BATCH".equals(source.type())).toList();
  }

  private String certificationState(BatchCertification certification) {
    return String.join(
        ":",
        certification.getCertification().getCertificationCode(),
        certification.getCertificateKind() == null
            ? "UNCLASSIFIED"
            : certification.getCertificateKind().name(),
        certification.getScope().name(),
        certification.getValidFrom() == null
            ? "NO_VALID_FROM"
            : certification.getValidFrom().toString(),
        certification.getValidUntil() == null
            ? "NO_VALID_UNTIL"
            : certification.getValidUntil().toString());
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public Inputs lockAndInspect(Requirements requirements) {
    requireTenant(requirements);
    entityManager
        .createNativeQuery("select set_config('lock_timeout', '5s', true)")
        .getSingleResult();
    // Flush does not commit. Preserve the caller's pending edits rather than refresh them away.
    entityManager.flush();
    var batchVersions =
        lockRows("production_execution_batch", "product_id", requirements.productIds());
    var ids = batchVersions.keySet();
    var locked =
        new LockedRows(
            batchVersions,
            lockRows("stock_unit", "batch_id", ids),
            lockRows("batch_lot_quantity_intent", "batch_id", ids),
            lockRows("production_execution_batch_reservation", "batch_id", ids),
            lockRows("production_execution_batch_certification", "batch_id", ids));
    return inspect(requirements, locked);
  }

  /** Table and column names are private code constants; only values are caller supplied. */
  private Map<UUID, Long> lockRows(String table, String foreignKey, Collection<UUID> ids) {
    if (ids.isEmpty()) return Map.of();
    List<?> rows =
        entityManager
            .createNativeQuery(
                "select id, version from production."
                    + table
                    + " where tenant_id = :tenant and is_active = true and "
                    + foreignKey
                    + " in (:ids) order by id for update")
            .setParameter("tenant", TenantContext.requireTenantId())
            .setParameter("ids", ids)
            .getResultList();
    Map<UUID, Long> result = new LinkedHashMap<>();
    rows.forEach(
        value -> {
          Object[] row = (Object[]) value;
          result.put((UUID) row[0], ((Number) row[1]).longValue());
        });
    return Map.copyOf(result);
  }

  private static void requireVersions(List<? extends BaseEntity> rows, Map<UUID, Long> locked) {
    if (rows.size() != locked.size()
        || rows.stream()
            .anyMatch(row -> !Objects.equals(locked.get(row.getId()), row.getVersion())))
      throw new OptimisticLockException("Order-cover production inputs changed; retry settlement");
  }

  private record LockedRows(
      Map<UUID, Long> batches,
      Map<UUID, Long> units,
      Map<UUID, Long> intents,
      Map<UUID, Long> reservations,
      Map<UUID, Long> certifications) {}

  private static Source source(String type, BaseEntity entity) {
    return new Source(
        type,
        entity.getId(),
        entity.getVersion(),
        null,
        entity.getCreatedAt(),
        SourceKnowledge.VERIFIED,
        null);
  }

  private static void requireTenant(Requirements requirements) {
    if (!TenantContext.requireTenantId().equals(requirements.tenantId()))
      throw new BatchDomainException(
          "Order-cover evidence not found", "BATCH_EVIDENCE_NOT_FOUND", 404);
  }
}
