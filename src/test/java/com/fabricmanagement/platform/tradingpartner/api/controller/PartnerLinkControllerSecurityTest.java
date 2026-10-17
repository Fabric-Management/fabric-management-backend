package com.fabricmanagement.platform.tradingpartner.api.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

/** Reading links needs sales:read; every change needs sales:write (CONSIGNEE-1 S1). */
class PartnerLinkControllerSecurityTest {

  private static final String READ = "@auth.can(authentication, 'sales', 'read')";
  private static final String WRITE = "@auth.can(authentication, 'sales', 'write')";

  @Test
  void everyEndpointIsGuardedByTheSalesPermissionPair() {
    Map<String, String> guards = new TreeMap<>();
    Arrays.stream(PartnerLinkController.class.getDeclaredMethods())
        .filter(method -> !method.isSynthetic())
        .forEach(method -> guards.put(method.getName(), preAuthorizeOf(method)));

    assertThat(guards)
        .isEqualTo(
            Map.of(
                "listLinks", READ,
                "linkExisting", WRITE,
                "quickCreateAndLink", WRITE,
                "unlink", WRITE));
  }

  private static String preAuthorizeOf(Method method) {
    PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);
    return annotation == null ? "<missing>" : annotation.value();
  }
}
