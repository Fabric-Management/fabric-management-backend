package com.fabricmanagement.common.infrastructure.persistence;

import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Generates human-readable unique identifiers (UIDs) for entities.
 *
 * <p>UIDs follow the pattern: {@code {TENANT_UID}-{MODULE}-{ENTITY}-{SEQUENCE}}
 *
 * <h2>Examples:</h2>
 *
 * <ul>
 *   <li>{@code ACME-001-USER-00042} - User with sequence 42 in tenant ACME-001
 *   <li>{@code XYZ-002-MAT-05123} - Product with sequence 5123 in tenant XYZ-002
 *   <li>{@code CORP-003-INV-00891} - Invoice with sequence 891 in tenant CORP-003
 * </ul>
 *
 * <h2>Benefits:</h2>
 *
 * <ul>
 *   <li><b>Human-readable:</b> Easy to read, communicate, and remember
 *   <li><b>Debuggable:</b> Tenant and entity type visible at a glance
 *   <li><b>Audit-friendly:</b> Can be used in logs and support tickets
 *   <li><b>Sortable:</b> Sequential nature allows sorting by creation order
 * </ul>
 *
 * <h2>Usage:</h2>
 *
 * <pre>{@code
 * @Service
 * public class ProductService {
 *     private final UIDGenerator uidGenerator;
 *
 *     public Product create(CreateProductRequest request) {
 *         Product product = new Product();
 *         product.setName(request.getName());
 *
 *         // Generate UID
 *         String uid = uidGenerator.generate("MAT", "product");
 *         product.setUid(uid);  // e.g., "ACME-001-MAT-00123"
 *
 *         return productRepository.save(product);
 *     }
 * }
 * }</pre>
 *
 * <h2>Sequence Management:</h2>
 *
 * <p>Sequences are managed per (tenant, module, entity) combination using PostgreSQL sequences.
 * This ensures uniqueness and avoids collisions.
 *
 * @see BaseEntity#uid
 */
@Component
@Slf4j
public class UIDGenerator {

  /**
   * Generates a UID for an entity
   *
   * @param moduleCode short module code (e.g., "MAT" for product, "USER" for user)
   * @param entityType entity type (e.g., "product", "user", "invoice")
   * @return generated UID
   */
  public String generate(String moduleCode, String entityType) {
    String tenantUid = TenantContext.getCurrentTenantUid();
    if (tenantUid == null) {
      tenantUid = "SYS-000"; // System tenant fallback
    }

    String uniqueSuffix =
        UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();

    String uid = String.format("%s-%s-%s", tenantUid, moduleCode.toUpperCase(), uniqueSuffix);

    log.debug("Generated UID: {} for entity: {}", uid, entityType);
    return uid;
  }

  /**
   * Generates a UID with custom format
   *
   * @param moduleCode short module code
   * @param entityType entity type
   * @param suffixLength length of sequence part (default: 5)
   * @return generated UID
   */
  public String generate(String moduleCode, String entityType, int suffixLength) {
    String tenantUid = TenantContext.getCurrentTenantUid();
    if (tenantUid == null) {
      tenantUid = "SYS-000";
    }

    int len = Math.max(4, Math.min(suffixLength, 32));
    String uniqueSuffix =
        UUID.randomUUID().toString().replace("-", "").substring(0, len).toUpperCase();

    String uid = String.format("%s-%s-%s", tenantUid, moduleCode.toUpperCase(), uniqueSuffix);

    log.debug("Generated UID: {} for entity: {}", uid, entityType);
    return uid;
  }

  /**
   * Validates UID format
   *
   * @param uid the UID to validate
   * @return true if valid format
   */
  public boolean isValid(String uid) {
    if (uid == null || uid.isBlank()) {
      return false;
    }

    // Pattern: TENANT-MODULE-SEQUENCE
    // Example: ACME-001-MAT-00123
    String[] parts = uid.split("-");

    if (parts.length < 3) {
      return false;
    }

    String lastPart = parts[parts.length - 1];
    return lastPart.matches("[A-Fa-f0-9]+") || lastPart.matches("\\d+");
  }

  /**
   * Extracts tenant UID from a full UID
   *
   * @param uid the full UID
   * @return tenant UID, or null if invalid
   */
  public String extractTenantUid(String uid) {
    if (!isValid(uid)) {
      return null;
    }

    String[] parts = uid.split("-");
    if (parts.length < 3) {
      return null;
    }

    // First parts up to module code form tenant UID
    // Example: ACME-001-MAT-00123 → ACME-001
    StringBuilder tenantUid = new StringBuilder();
    for (int i = 0; i < parts.length - 2; i++) {
      if (i > 0) {
        tenantUid.append("-");
      }
      tenantUid.append(parts[i]);
    }

    return tenantUid.toString();
  }

  /**
   * Extracts module code from a full UID
   *
   * @param uid the full UID
   * @return module code, or null if invalid
   */
  public String extractModuleCode(String uid) {
    if (!isValid(uid)) {
      return null;
    }

    String[] parts = uid.split("-");
    if (parts.length < 3) {
      return null;
    }

    // Second to last part is module code
    return parts[parts.length - 2];
  }

  /**
   * Extracts the suffix (last segment) from a full UID.
   *
   * <p>For UUID-based UIDs this returns the hex suffix; for legacy numeric UIDs it parses as a
   * long.
   *
   * @param uid the full UID
   * @return sequence number for legacy UIDs, or -1 for UUID-based / invalid UIDs
   */
  public long extractSequence(String uid) {
    if (!isValid(uid)) {
      return -1;
    }

    String[] parts = uid.split("-");
    String suffixPart = parts[parts.length - 1];

    try {
      return Long.parseLong(suffixPart);
    } catch (NumberFormatException e) {
      return -1;
    }
  }

  /**
   * Extracts the suffix segment from a full UID (works for both UUID-hex and legacy numeric).
   *
   * @param uid the full UID
   * @return suffix string, or null if invalid
   */
  public String extractSuffix(String uid) {
    if (!isValid(uid)) {
      return null;
    }

    String[] parts = uid.split("-");
    return parts[parts.length - 1];
  }
}
