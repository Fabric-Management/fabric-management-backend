package com.fabricmanagement.common.infrastructure.assignment;

import com.fabricmanagement.common.infrastructure.persistence.Assignable;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Generic base service for parent–child assignment (junction) operations.
 *
 * <p>Provides DRY assign/unassign/setPrimary/getByParent logic. Subclasses implement repository
 * access and entity building; optional hooks {@link #validateAssignment} and {@link #onAfterAssign}
 * for business rules.
 *
 * @param <J> Junction entity type (e.g. CompanyContact, CompanyAddress, UserContact, UserAddress)
 *     implementing {@link Assignable}
 */
@Slf4j
public abstract class BaseAssignmentService<J extends Assignable> {

  /** Repository for the junction entity (save, delete, and queries via abstract find methods). */
  protected abstract JpaRepository<J, ?> getRepository();

  /** Validate that the parent entity exists in the current tenant; throw if not. */
  protected abstract void validateParentExists(UUID parentId);

  /** Validate that the child entity exists and belongs to the current tenant; throw if not. */
  protected abstract void validateChildExists(UUID childId);

  /** Find existing assignment by parent and child IDs. */
  protected abstract Optional<J> findExisting(UUID parentId, UUID childId);

  /** Find the primary/default assignment for the parent. */
  protected abstract Optional<J> findPrimaryByParent(UUID parentId);

  /** Find all assignments for the parent (tenant-scoped). */
  protected abstract List<J> findByParent(UUID parentId);

  /** Build a new junction entity (not yet persisted). */
  protected abstract J buildJunction(UUID parentId, UUID childId, Boolean primaryFlag);

  /** Optional hook to validate assignment rules (e.g. "only one headquarters"). Default no-op. */
  @SuppressWarnings("unused")
  protected void validateAssignment(UUID parentId, UUID childId) {}

  /** Optional hook after assign (e.g. publish event). Default no-op. */
  @SuppressWarnings("unused")
  protected void onAfterAssign(J junction) {}

  @Transactional
  public J assign(UUID parentId, UUID childId, Boolean primaryFlag) {
    log.info("Assigning: parentId={}, childId={}, primary={}", parentId, childId, primaryFlag);

    validateParentExists(parentId);
    validateChildExists(childId);
    validateAssignment(parentId, childId);

    if (findExisting(parentId, childId).isPresent()) {
      throw new IllegalArgumentException("Assignment already exists");
    }

    if (Boolean.TRUE.equals(primaryFlag)) {
      findPrimaryByParent(parentId)
          .ifPresent(
              existing -> {
                existing.setPrimaryFlag(false);
                getRepository().save(existing);
              });
    }

    J junction = buildJunction(parentId, childId, primaryFlag != null ? primaryFlag : false);
    J saved = getRepository().save(junction);
    onAfterAssign(saved);
    return saved;
  }

  @Transactional
  public void unassign(UUID parentId, UUID childId) {
    log.info("Unassigning: parentId={}, childId={}", parentId, childId);

    J existing =
        findExisting(parentId, childId)
            .orElseThrow(() -> new IllegalArgumentException("Assignment not found"));
    getRepository().delete(existing);
  }

  @Transactional
  public J setPrimary(UUID parentId, UUID childId) {
    log.info("Setting primary: parentId={}, childId={}", parentId, childId);

    J junction =
        findExisting(parentId, childId)
            .orElseThrow(() -> new IllegalArgumentException("Assignment not found"));

    findPrimaryByParent(parentId)
        .ifPresent(
            existing -> {
              if (!existing.getChildId().equals(childId)) {
                existing.setPrimaryFlag(false);
                getRepository().save(existing);
              }
            });

    junction.setPrimaryFlag(true);
    return getRepository().save(junction);
  }

  @Transactional(readOnly = true)
  public Optional<J> getPrimary(UUID parentId) {
    return findPrimaryByParent(parentId);
  }

  @Transactional(readOnly = true)
  public List<J> getByParent(UUID parentId) {
    return findByParent(parentId);
  }
}
