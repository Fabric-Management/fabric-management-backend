package com.fabricmanagement.sales.ownership.domain;

public enum OwnerResolutionReason {
  EXPLICIT_OVERRIDE,
  PRIMARY_ASSIGNMENT,
  CREATOR_FALLBACK,
  OPTIONAL_UNASSIGNED,
  OWNERSHIP_EXEMPT,
  LEGACY_UNKNOWN,
  ACQUIRER,
  ACCOUNT_TEAM,
  TRIAGE_REQUIRED
}
