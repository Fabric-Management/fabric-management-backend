package com.fabricmanagement.offline.domain;

import java.util.Set;

/**
 * Indicates the synchronization state of an offline-created entity.
 *
 * <h2>State Machine</h2>
 *
 * <pre>
 *   PENDING ──→ SYNCED   (sync başarılı)
 *   PENDING ──→ CONFLICT (çakışma tespit edildi)
 *   CONFLICT ──→ RESOLVED (manager çözdü)
 *   RESOLVED ──→ SYNCED   (re-sync başarılı)
 * </pre>
 *
 * <p>Terminal state: {@link #SYNCED} — bir kez SYNCED olan entity tekrar PENDING'e dönemez.
 */
public enum SyncStatus {
  /** Entity is created/updated offline and waiting to be synced to the backend. */
  PENDING {
    @Override
    public Set<SyncStatus> allowedTransitions() {
      return Set.of(SYNCED, CONFLICT);
    }
  },

  /** Entity has been successfully synced and confirmed by the backend. Terminal state. */
  SYNCED {
    @Override
    public Set<SyncStatus> allowedTransitions() {
      return Set.of(); // terminal
    }
  },

  /** A synchronization conflict occurred, requiring manual resolution. */
  CONFLICT {
    @Override
    public Set<SyncStatus> allowedTransitions() {
      return Set.of(RESOLVED);
    }
  },

  /** A conflict was reviewed and resolved by a manager/user, ready for re-sync. */
  RESOLVED {
    @Override
    public Set<SyncStatus> allowedTransitions() {
      return Set.of(SYNCED);
    }
  };

  /** Returns the set of statuses this status can transition to. */
  public abstract Set<SyncStatus> allowedTransitions();

  /** Checks if transitioning to the target status is allowed. */
  public boolean canTransitionTo(SyncStatus target) {
    return allowedTransitions().contains(target);
  }
}
