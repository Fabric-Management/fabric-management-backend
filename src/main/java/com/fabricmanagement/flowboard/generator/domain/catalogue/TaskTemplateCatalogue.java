package com.fabricmanagement.flowboard.generator.domain.catalogue;

import com.fabricmanagement.flowboard.task.domain.TaskType;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.StringJoiner;

/**
 * The platform task-template catalogue (TASK-TEMPLATE-TENANCY-1 §4–§5).
 *
 * <p>Each entry is one adapter-served {@code (eventType, taskType)} job with its stable catalogue
 * key and the seed signature a pre-existing unkeyed row must carry to be adopted (R2). The same
 * pins are repeated in {@code V20260926100100__task_template_catalog_golden.sql}; {@code
 * TaskTemplateCatalogueFingerprintIT} keeps the two from drifting apart.
 */
public enum TaskTemplateCatalogue {
  SALES_ORDER_CONFIRMED__PLANNING(
      "SalesOrderConfirmed",
      TaskType.PLANNING,
      "Auto Template",
      UidRule.NULL,
      null,
      "eb736401146175ef43672b5d89d51e2a"),
  WORK_ORDER_APPROVED__PRODUCTION(
      "WorkOrderApproved",
      TaskType.PRODUCTION,
      "Auto Template",
      UidRule.NULL,
      null,
      "5fee86f6508c7fcba300a72e2cf2db8b"),
  GOODS_RECEIPT_CONFIRMED__WAREHOUSE(
      "GoodsReceiptConfirmed",
      TaskType.WAREHOUSE,
      "Auto Template",
      UidRule.NULL,
      null,
      "b69878f426ea372145af021508b02e71"),
  QUOTE_SEND_REQUESTED__APPROVAL(
      "QuoteSendRequested",
      TaskType.APPROVAL,
      "Quote send approval",
      UidRule.ANY,
      null,
      "f60aeab289657754c5cb2521cefc6431"),
  WORK_ORDER_RECIPE_ASSIGNMENT_NEEDED__RECIPE_ASSIGNMENT(
      "WorkOrderRecipeAssignmentNeeded",
      TaskType.RECIPE_ASSIGNMENT,
      "Recipe Assignment Required",
      UidRule.FIXED,
      "SYS-TMPL-RECIPE-ASSIGN",
      "12428190ed4797c168cf7e4a0ab6c451");

  /** How a seeded row's {@code uid} must look for R2 adoption. */
  public enum UidRule {
    /** Seeded without uid ({@code V002}). */
    NULL,
    /** Seeded with a random uid per row ({@code V20260706120000}). */
    ANY,
    /** Seeded with one fixed uid. */
    FIXED
  }

  private final String eventType;
  private final TaskType taskType;
  private final String seedName;
  private final UidRule uidRule;
  private final String fixedUid;
  private final String seedFingerprint;

  TaskTemplateCatalogue(
      String eventType,
      TaskType taskType,
      String seedName,
      UidRule uidRule,
      String fixedUid,
      String seedFingerprint) {
    this.eventType = eventType;
    this.taskType = taskType;
    this.seedName = seedName;
    this.uidRule = uidRule;
    this.fixedUid = fixedUid;
    this.seedFingerprint = seedFingerprint;
  }

  /** The value stored in {@code task_template.catalog_key}. */
  public String key() {
    return name();
  }

  public String eventType() {
    return eventType;
  }

  public TaskType taskType() {
    return taskType;
  }

  public String seedName() {
    return seedName;
  }

  public String seedFingerprint() {
    return seedFingerprint;
  }

  /** Whether {@code uid} satisfies this entry's seed uid rule. */
  public boolean uidMatchesSeed(String uid) {
    return switch (uidRule) {
      case NULL -> uid == null;
      case ANY -> true;
      case FIXED -> fixedUid.equals(uid);
    };
  }

  public static Optional<TaskTemplateCatalogue> byKey(String key) {
    if (key == null) {
      return Optional.empty();
    }
    for (TaskTemplateCatalogue entry : values()) {
      if (entry.key().equals(key)) {
        return Optional.of(entry);
      }
    }
    return Optional.empty();
  }

  /**
   * Content fingerprint, identical to the SQL expression {@code md5(concat_ws('|', title_template,
   * task_type, module_type, default_priority, default_assignee_role, estimated_hours, auto_labels,
   * checklist_template))}: NULLs are skipped, {@code estimated_hours} renders as stored (scale 2).
   *
   * <p>MD5 is used only as a content fingerprint that must match PostgreSQL {@code md5()}; it is
   * not a security control. The SpotBugs WEAK_MESSAGE_DIGEST_MD5 finding is excluded for this class
   * in {@code spotbugs-exclude.xml}.
   */
  public static String fingerprint(
      String titleTemplate,
      String taskType,
      String moduleType,
      String defaultPriority,
      String defaultAssigneeRole,
      BigDecimal estimatedHours,
      String autoLabels,
      String checklistTemplate) {
    StringJoiner joined = new StringJoiner("|");
    add(joined, titleTemplate);
    add(joined, taskType);
    add(joined, moduleType);
    add(joined, defaultPriority);
    add(joined, defaultAssigneeRole);
    add(joined, estimatedHours == null ? null : estimatedHours.toPlainString());
    add(joined, autoLabels);
    add(joined, checklistTemplate);
    try {
      byte[] digest =
          MessageDigest.getInstance("MD5")
              .digest(joined.toString().getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("MD5 not available", e);
    }
  }

  private static void add(StringJoiner joined, String value) {
    if (value != null) {
      joined.add(value);
    }
  }
}
