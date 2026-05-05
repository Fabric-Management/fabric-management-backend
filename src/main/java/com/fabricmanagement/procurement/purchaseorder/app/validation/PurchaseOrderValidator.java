package com.fabricmanagement.procurement.purchaseorder.app.validation;

import com.fabricmanagement.procurement.purchaseorder.domain.PurchaseOrderModuleType;
import com.fabricmanagement.procurement.purchaseorder.domain.specs.PurchaseOrderSpecs;
import java.util.List;

/**
 * Strategy interface for module-specific PurchaseOrder validation.
 *
 * <p>Each implementation handles one {@link PurchaseOrderModuleType} and provides two validation
 * phases:
 *
 * <ul>
 *   <li>{@link #validateOnCreate} — format and range checks (runs on create AND update)
 *   <li>{@link #validateOnConfirm} — completeness checks (runs on DRAFT→SENT transition)
 * </ul>
 *
 * <p><b>Design rule:</b> Any mutation of {@code moduleSpecs} (create, update) MUST trigger {@link
 * #validateOnCreate}. The engine ensures {@link #validateOnConfirm} also re-runs create rules as a
 * safety net.
 */
public interface PurchaseOrderValidator {

  /** Returns the module type this validator is responsible for. */
  PurchaseOrderModuleType getSupportedType();

  /**
   * Format and range validations — runs on PO creation and any specs update.
   *
   * <p>Examples: GSM 80-400, grade must be A/B/C/SECOND, twist must be S/Z.
   *
   * @return list of violation messages (empty if valid)
   */
  List<String> validateOnCreate(PurchaseOrderSpecs specs);

  /**
   * Completeness validations — runs when transitioning PO to SENT status.
   *
   * <p>Examples: approvalDocumentId required, yarnCount must not be blank.
   *
   * <p><b>Note:</b> The engine runs {@link #validateOnCreate} + this method together during
   * confirm. Implementations should only add confirm-specific rules here, not duplicate create
   * rules.
   *
   * @return list of violation messages (empty if valid)
   */
  List<String> validateOnConfirm(PurchaseOrderSpecs specs);
}
