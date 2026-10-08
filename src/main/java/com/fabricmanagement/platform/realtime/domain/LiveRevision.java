package com.fabricmanagement.platform.realtime.domain;

/**
 * An opaque, non-empty revision token of a watched resource. The channel only compares two tokens
 * for equality; it never orders or does arithmetic on them, so a source may use any committed
 * marker that changes whenever the resource's watched state changes.
 */
public record LiveRevision(String value) {

  public LiveRevision {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("A live revision must not be empty");
    }
  }

  /** The decimal form of a numeric version; version 0 is a valid revision. */
  public static LiveRevision of(long version) {
    return new LiveRevision(Long.toString(version));
  }
}
