package com.fabricmanagement.platform.user.app;

import java.util.Collections;
import java.util.List;

/**
 * Single place for nav preferences defaults.
 *
 * <p>When no stored preferences exist, GET returns these. When creating a new row, null request
 * fields are treated as "use default". Change here to introduce a system-default sort order (e.g.
 * canonical nav item IDs) if needed.
 */
public final class NavPreferencesConstants {

  private NavPreferencesConstants() {}

  /** Default sort order when none is stored or provided. Empty = client/frontend decides order. */
  public static final List<String> DEFAULT_SORT_ORDER = Collections.emptyList();

  /** Default hidden item IDs when none are stored or provided. */
  public static final List<String> DEFAULT_HIDDEN_ITEM_IDS = Collections.emptyList();
}
