package com.fabricmanagement.platform.realtime.domain;

import java.util.UUID;

/**
 * The read port a consuming module implements for one kind of live resource (CEDIT-05 §2). The
 * channel calls it when a connection opens and on every check, always inside the actor's tenant
 * scope and a short read-only transaction of its own, so an implementation reads only committed
 * data and must not rely on anything loaded by an earlier call.
 *
 * <p>Every call decides access again from current data: who the actor is now, the actor's current
 * permission and scope and the resource's current state. A cached or earlier answer is never proof.
 */
public interface LiveRevisionSource {

  /** A stable, server-chosen name of the resource kind; used for logs and metrics, never ids. */
  String resourceType();

  /**
   * The resource's committed revision if the actor may read it now, otherwise why not. Called with
   * the actor's tenant bound; it must not open its own transaction or switch tenants.
   */
  LiveReadResult read(LiveActor actor, UUID resourceId);
}
