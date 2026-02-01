package com.fabricmanagement.common.infrastructure.persistence;

import java.util.UUID;

/**
 * Marker interface for junction/assignment entities (CompanyContact, CompanyAddress, UserContact,
 * UserAddress).
 *
 * <p>Used by {@link com.fabricmanagement.common.infrastructure.assignment.BaseAssignmentService} to
 * provide DRY assign/unassign/setPrimary/getByParent logic. Primary flag semantics vary: Contact
 * junctions use "isDefault", Address junctions use "isPrimary" — both map to
 * getPrimaryFlag/setPrimaryFlag.
 */
public interface Assignable {

  UUID getParentId();

  UUID getChildId();

  /**
   * Primary/default flag for this assignment. For contact junctions this is isDefault; for address
   * junctions this is isPrimary.
   */
  Boolean getPrimaryFlag();

  void setPrimaryFlag(Boolean value);
}
