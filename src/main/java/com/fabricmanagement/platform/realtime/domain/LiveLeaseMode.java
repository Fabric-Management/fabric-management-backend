package com.fabricmanagement.platform.realtime.domain;

/**
 * Whether field leases are enforced for one kind of resource in one tenant (CEDIT-07 §3.4). The
 * mode is stored in PostgreSQL, so every instance decides alike; no request, header, restart or
 * closed stream changes it.
 *
 * <ul>
 *   <li>{@link #OFF}: the default. No lease is granted and a save needs none; a lease that still
 *       lives (left from an earlier enforcement) is honoured all the same, so lowering the mode
 *       never frees a field someone holds.
 *   <li>{@link #ENFORCED}: every write of a leasable field proves the writer's own lease; other
 *       writers are refused while somebody holds an overlapping lease. Once enforced, the
 *       application never lowers it.
 * </ul>
 */
public enum LiveLeaseMode {
  OFF,
  ENFORCED
}
