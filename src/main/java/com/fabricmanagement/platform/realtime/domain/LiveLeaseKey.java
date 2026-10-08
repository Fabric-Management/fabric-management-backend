package com.fabricmanagement.platform.realtime.domain;

import java.util.Comparator;
import java.util.regex.Pattern;

/**
 * What one field lease covers (CEDIT-07 §3.2), domain-agnostic: a {@code scope} (a part of the
 * resource, such as the header or one line) and a {@code part} inside it. The part {@link #WHOLE}
 * covers the whole scope: it overlaps every part of the same scope, so holding a whole line and
 * holding one of its fields exclude each other across sessions. Two different parts of one scope
 * and anything in another scope never overlap.
 *
 * <p>The consuming module maps its own finite catalogue onto keys; a free client path never becomes
 * a key. Keys are ordered by scope then part: every statement that locks lease rows locks them in
 * this order, so two lease writers cannot deadlock each other.
 */
public record LiveLeaseKey(String scope, String part) implements Comparable<LiveLeaseKey> {

  /** The part that stands for the whole scope. */
  public static final String WHOLE = "*";

  private static final Pattern SCOPE = Pattern.compile("^[a-z][a-z0-9:-]{0,79}$");
  private static final Pattern PART = Pattern.compile("^(\\*|[a-z][A-Za-z0-9.]{0,59})$");

  private static final Comparator<LiveLeaseKey> ORDER =
      Comparator.comparing(LiveLeaseKey::scope).thenComparing(LiveLeaseKey::part);

  public LiveLeaseKey {
    if (scope == null || !SCOPE.matcher(scope).matches()) {
      throw new IllegalArgumentException("A lease scope is a short lower-case name: " + scope);
    }
    if (part == null || !PART.matcher(part).matches()) {
      throw new IllegalArgumentException("A lease part is a field name or *: " + part);
    }
  }

  /** The key of a whole scope. */
  public static LiveLeaseKey whole(String scope) {
    return new LiveLeaseKey(scope, WHOLE);
  }

  public boolean isWhole() {
    return WHOLE.equals(part);
  }

  /** Whether one session holding this key and another holding {@code other} would collide. */
  public boolean overlaps(LiveLeaseKey other) {
    return scope.equals(other.scope) && (part.equals(other.part) || isWhole() || other.isWhole());
  }

  @Override
  public int compareTo(LiveLeaseKey other) {
    return ORDER.compare(this, other);
  }
}
