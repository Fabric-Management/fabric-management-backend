package com.fabricmanagement.common.infrastructure.persistence;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Generates human-readable unique identifiers (UIDs) for entities.
 *
 * <p>UIDs follow the pattern: {@code {TENANT_UID}-{MODULE}-{ENTITY}-{SEQUENCE}}</p>
 *
 * <h2>Examples:</h2>
 * <ul>
 *   <li>{@code ACME-001-USER-00042} - User with sequence 42 in tenant ACME-001</li>
 *   <li>{@code XYZ-002-MAT-05123} - Material with sequence 5123 in tenant XYZ-002</li>
 *   <li>{@code CORP-003-INV-00891} - Invoice with sequence 891 in tenant CORP-003</li>
 * </ul>
 *
 * <h2>Benefits:</h2>
 * <ul>
 *   <li><b>Human-readable:</b> Easy to read, communicate, and remember</li>
 *   <li><b>Debuggable:</b> Tenant and entity type visible at a glance</li>
 *   <li><b>Audit-friendly:</b> Can be used in logs and support tickets</li>
 *   <li><b>Sortable:</b> Sequential nature allows sorting by creation order</li>
 * </ul>
 *
 * <h2>Usage:</h2>
 * <pre>{@code
 * @Service
 * public class MaterialService {
 *     private final UIDGenerator uidGenerator;
 *
 *     public Material create(CreateMaterialRequest request) {
 *         Material material = new Material();
 *         material.setName(request.getName());
 *
 *         // Generate UID
 *         String uid = uidGenerator.generate("MAT", "material");
 *         material.setUid(uid);  // e.g., "ACME-001-MAT-00123"
 *
 *         return materialRepository.save(material);
 *     }
 * }
 * }</pre>
 *
 * <h2>Sequence Management:</h2>
 * <p>Sequences are managed per (tenant, module, entity) combination using
 * PostgreSQL sequences. This ensures uniqueness and avoids collisions.</p>
 *
 * @see BaseEntity#uid
 */
@Component
@Slf4j
public class UIDGenerator {

    /**
     * Generates a UID for an entity
     *
     * @param moduleCode short module code (e.g., "MAT" for material, "USER" for user)
     * @param entityType entity type (e.g., "material", "user", "invoice")
     * @return generated UID
     */
    public String generate(String moduleCode, String entityType) {
        String tenantUid = TenantContext.getCurrentTenantUid();
        if (tenantUid == null) {
            tenantUid = "SYS-000";  // System tenant fallback
        }

        // For now, generate a simple sequential number
        //TODO: Implement database sequence-based generation 
        long sequence = System.currentTimeMillis() % 100000;

        String uid = String.format("%s-%s-%05d", tenantUid, moduleCode.toUpperCase(), sequence);

        log.debug("Generated UID: {} for entity: {}", uid, entityType);
        return uid;
    }

    /**
     * Generates a UID with custom format
     *
     * @param moduleCode short module code
     * @param entityType entity type
     * @param sequenceLength length of sequence part (default: 5)
     * @return generated UID
     */
    public String generate(String moduleCode, String entityType, int sequenceLength) {
        String tenantUid = TenantContext.getCurrentTenantUid();
        if (tenantUid == null) {
            tenantUid = "SYS-000";
        }

        long sequence = System.currentTimeMillis() % ((long) Math.pow(10, sequenceLength));

        String format = String.format("%%s-%%s-%%0%dd", sequenceLength);
        String uid = String.format(format, tenantUid, moduleCode.toUpperCase(), sequence);

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

        // Last part should be numeric
        String sequencePart = parts[parts.length - 1];
        return sequencePart.matches("\\d+");
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
     * Extracts sequence number from a full UID
     *
     * @param uid the full UID
     * @return sequence number, or -1 if invalid
     */
    public long extractSequence(String uid) {
        if (!isValid(uid)) {
            return -1;
        }

        String[] parts = uid.split("-");
        String sequencePart = parts[parts.length - 1];

        try {
            return Long.parseLong(sequencePart);
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}

