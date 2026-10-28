package com.fabricmanagement.production.core.batch.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import com.fabricmanagement.production.core.batch.domain.exception.BatchDomainException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One finishing process done to a fabric lot (FABRIC-3b): dyeing, printing, a mechanical finish.
 * The lot stays one lot while steps accumulate on it; a step records where it was done (an own
 * site, or a subcontractor), the number that firm gave the job, the metres that left and came back,
 * and what it cost. The lot's state — greige, dyed, printed, coated — is read from the categories
 * of its {@code DONE} steps, never stored on the lot.
 *
 * <p>A {@code SENT} step holds the lot out of sale; {@code receive} brings it back with the metres
 * the firm returned. The metres on a step are a record of the job: this entity never changes the
 * lot's own quantity.
 */
@Entity
@Table(name = "batch_finishing_step", schema = "production")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BatchFinishingStep extends BaseEntity {

  private static final int METRE_SCALE = 3;
  private static final int MAX_JOB_NUMBER = 100;

  @Column(name = "batch_id", nullable = false, updatable = false)
  private UUID batchId;

  @Column(name = "sequence", nullable = false, updatable = false)
  private int sequence;

  @Column(name = "process_id", nullable = false, updatable = false)
  private UUID processId;

  /** The subcontractor; null when the step was done at an own site. */
  @Column(name = "partner_id", updatable = false)
  private UUID partnerId;

  /** The number the firm gave the job (their lot, job or order number); how we find it again. */
  @Column(name = "firm_job_number", length = MAX_JOB_NUMBER)
  private String firmJobNumber;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private BatchFinishingStepStatus status;

  @Column(name = "sent_metres", nullable = false, updatable = false, precision = 15, scale = 3)
  private BigDecimal sentMetres;

  @Column(name = "sent_on", nullable = false, updatable = false)
  private LocalDate sentOn;

  @Column(name = "returned_metres", precision = 15, scale = 3)
  private BigDecimal returnedMetres;

  @Column(name = "returned_on")
  private LocalDate returnedOn;

  /** The colour the step gives the lot; only dyeing and printing carry one. */
  @Column(name = "colour_id", updatable = false)
  private UUID colourId;

  @Column(name = "cost_amount", precision = 15, scale = 2)
  private BigDecimal costAmount;

  @Column(name = "cost_currency", length = 3)
  private String costCurrency;

  @Column(name = "note", columnDefinition = "TEXT")
  private String note;

  /** The lot goes out; the caller has checked it may. */
  public static BatchFinishingStep send(
      UUID tenantId,
      UUID batchId,
      int sequence,
      UUID processId,
      UUID partnerId,
      String firmJobNumber,
      BigDecimal sentMetres,
      LocalDate sentOn,
      UUID colourId,
      String note) {
    BatchFinishingStep step = open(tenantId, batchId, sequence, processId, partnerId);
    step.status = BatchFinishingStepStatus.SENT;
    step.firmJobNumber = trimJobNumber(firmJobNumber);
    step.sentMetres = requireMetres(sentMetres, "Sent metres");
    step.sentOn = requireDate(sentOn, "Sent date");
    step.colourId = colourId;
    step.note = trimToNull(note);
    return step;
  }

  /**
   * A step that was finished before the lot came to us (a dyed lot bought in): recorded as done,
   * without sending anything anywhere.
   */
  public static BatchFinishingStep recordDone(
      UUID tenantId,
      UUID batchId,
      int sequence,
      UUID processId,
      UUID partnerId,
      String firmJobNumber,
      BigDecimal sentMetres,
      LocalDate sentOn,
      BigDecimal returnedMetres,
      LocalDate returnedOn,
      UUID colourId,
      BigDecimal costAmount,
      String costCurrency,
      String note) {
    BatchFinishingStep step =
        send(
            tenantId,
            batchId,
            sequence,
            processId,
            partnerId,
            firmJobNumber,
            sentMetres,
            sentOn,
            colourId,
            note);
    step.complete(returnedMetres, returnedOn, costAmount, costCurrency);
    return step;
  }

  /** The lot is back with the metres the firm returned. */
  public void receive(
      BigDecimal returnedMetres,
      LocalDate returnedOn,
      String firmJobNumber,
      BigDecimal costAmount,
      String costCurrency,
      String note) {
    requireSent("received");
    if (firmJobNumber != null && !firmJobNumber.isBlank()) {
      this.firmJobNumber = trimJobNumber(firmJobNumber);
    }
    if (note != null && !note.isBlank()) {
      this.note = note.trim();
    }
    complete(returnedMetres, returnedOn, costAmount, costCurrency);
  }

  /** The entry was a mistake and the lot never left; only a step still out can be withdrawn. */
  public void cancel(String reason) {
    requireSent("cancelled");
    if (reason == null || reason.isBlank()) {
      throw new BatchDomainException(
          "A reason is required to cancel a finishing step",
          "FINISHING_STEP_CANCEL_REASON_REQUIRED",
          422);
    }
    this.status = BatchFinishingStepStatus.CANCELLED;
    this.note = reason.trim();
    onUpdate();
  }

  /** Metres lost in the process: sent minus returned; negative when the fabric came back longer. */
  public BigDecimal lossMetres() {
    return returnedMetres == null ? null : sentMetres.subtract(returnedMetres);
  }

  public boolean isOut() {
    return status == BatchFinishingStepStatus.SENT;
  }

  public boolean isDone() {
    return status == BatchFinishingStepStatus.DONE;
  }

  private void complete(
      BigDecimal returnedMetres, LocalDate returnedOn, BigDecimal costAmount, String costCurrency) {
    BigDecimal metres = requireMetres(returnedMetres, "Returned metres");
    LocalDate date = requireDate(returnedOn, "Return date");
    if (date.isBefore(sentOn)) {
      throw new BatchDomainException(
          "A finishing step cannot return before it was sent",
          "FINISHING_STEP_RETURN_BEFORE_SEND",
          422);
    }
    boolean hasAmount = costAmount != null;
    boolean hasCurrency = costCurrency != null && !costCurrency.isBlank();
    if (hasAmount != hasCurrency) {
      throw new BatchDomainException(
          "A cost needs both an amount and a currency", "FINISHING_STEP_COST_INCOMPLETE", 422);
    }
    if (hasAmount) {
      if (costAmount.signum() < 0 || costAmount.stripTrailingZeros().scale() > 2) {
        throw new BatchDomainException(
            "A cost is zero or more, with at most two decimals",
            "FINISHING_STEP_COST_INVALID",
            422);
      }
      String currency = costCurrency.trim().toUpperCase(Locale.ROOT);
      if (!currency.matches("[A-Z]{3}")) {
        throw new BatchDomainException(
            "A cost currency is a three-letter code", "FINISHING_STEP_COST_INVALID", 422);
      }
      this.costAmount = costAmount.setScale(2, RoundingMode.UNNECESSARY);
      this.costCurrency = currency;
    }
    this.returnedMetres = metres;
    this.returnedOn = date;
    this.status = BatchFinishingStepStatus.DONE;
    onUpdate();
  }

  private void requireSent(String action) {
    if (status != BatchFinishingStepStatus.SENT) {
      throw new BatchDomainException(
          "A finishing step in status " + status + " cannot be " + action,
          "FINISHING_STEP_NOT_OUT",
          422);
    }
  }

  private static BatchFinishingStep open(
      UUID tenantId, UUID batchId, int sequence, UUID processId, UUID partnerId) {
    if (tenantId == null || batchId == null || processId == null) {
      throw new IllegalArgumentException("Tenant, lot and process are required");
    }
    if (sequence < 1) {
      throw new IllegalArgumentException("A finishing step sequence starts at 1");
    }
    BatchFinishingStep step = new BatchFinishingStep();
    step.setTenantId(tenantId);
    step.batchId = batchId;
    step.sequence = sequence;
    step.processId = processId;
    step.partnerId = partnerId;
    return step;
  }

  private static BigDecimal requireMetres(BigDecimal value, String label) {
    if (value == null || value.signum() <= 0) {
      throw new BatchDomainException(
          label + " must be positive", "FINISHING_STEP_METRES_INVALID", 422);
    }
    if (value.stripTrailingZeros().scale() > METRE_SCALE) {
      throw new BatchDomainException(
          label + " allows at most three decimals", "FINISHING_STEP_METRES_INVALID", 422);
    }
    return value.setScale(METRE_SCALE, RoundingMode.UNNECESSARY);
  }

  private static LocalDate requireDate(LocalDate value, String label) {
    if (value == null) {
      throw new BatchDomainException(label + " is required", "FINISHING_STEP_DATE_REQUIRED", 422);
    }
    return value;
  }

  private static String trimJobNumber(String value) {
    String trimmed = trimToNull(value);
    if (trimmed != null && trimmed.length() > MAX_JOB_NUMBER) {
      throw new BatchDomainException(
          "A firm job number is at most " + MAX_JOB_NUMBER + " characters",
          "FINISHING_STEP_JOB_NUMBER_TOO_LONG",
          422);
    }
    return trimmed;
  }

  private static String trimToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  @Override
  protected String getModuleCode() {
    return "BFS";
  }
}
