package com.fabricmanagement.flowboard.decision.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Display-only evidence verdict exposed by the shared decision queue contract. */
@Schema(name = "DecisionVerdictCode")
public enum DecisionVerdictCode {
  ACTIONABLE,
  EVIDENCE_UNKNOWN,
  NO_EVIDENCE,
  CASE_CLOSED
}
