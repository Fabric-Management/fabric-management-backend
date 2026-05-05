package com.fabricmanagement.production.execution.workorder.domain;

/**
 * Manufacturing module types representing physical production processes.
 *
 * <p>Unlike procurement which has combined modules (e.g., DYE_FINISHING for subcontracting),
 * internal manufacturing treats DYEING and FINISHING as separate processes due to different machine
 * and operator requirements.
 */
public enum WorkOrderModuleType {
  SPINNING, // Fiber → Yarn
  WEAVING, // Yarn → Fabric (woven)
  KNITTING, // Yarn → Fabric (knit)
  DYEING, // Fabric/Yarn → Dyed
  FINISHING, // Dyed → Finished
  GENERIC // Catch-all or undefined
}
