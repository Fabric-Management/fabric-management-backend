package com.fabricmanagement.platform.realtime.domain;

/**
 * What a source answers for one actor and one resource at one moment: the committed revision when
 * the actor may read the resource now, otherwise why not. Both hidden answers end a running
 * connection the same way; only the opening request tells them apart (403 versus 404).
 */
public sealed interface LiveReadResult permits LiveReadResult.Visible, LiveReadResult.Hidden {

  /**
   * The actor may read the resource; this is its committed revision. {@code presence} is a separate
   * marker of who edits the resource now (CEDIT-06), or null for a resource without presence. The
   * two are never combined: each tells the client what to read again.
   */
  record Visible(LiveRevision revision, LiveRevision presence) implements LiveReadResult {

    public Visible {
      if (revision == null) {
        throw new IllegalArgumentException("A visible resource has a revision");
      }
    }

    /** A resource without presence. */
    public Visible(LiveRevision revision) {
      this(revision, null);
    }
  }

  /** The actor may not read the resource now. */
  enum Hidden implements LiveReadResult {
    /** The actor has no read permission of this kind at all (or is no longer an active user). */
    FORBIDDEN,
    /** The resource does not exist, is deleted, or lies outside the actor's tenant or scope. */
    NOT_FOUND
  }
}
