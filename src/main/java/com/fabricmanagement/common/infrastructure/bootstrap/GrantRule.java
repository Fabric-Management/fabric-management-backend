package com.fabricmanagement.common.infrastructure.bootstrap;

import com.fabricmanagement.common.infrastructure.security.PermissionKey;
import com.fabricmanagement.platform.user.domain.DataScope;
import java.util.Objects;

/** A grant chooses one valid permission pair, never independent resource/action strings. */
record GrantRule(String roleCode, PermissionKey key, DataScope scope) {
  GrantRule {
    Objects.requireNonNull(roleCode, "roleCode");
    Objects.requireNonNull(key, "key");
    Objects.requireNonNull(scope, "scope");
  }
}
