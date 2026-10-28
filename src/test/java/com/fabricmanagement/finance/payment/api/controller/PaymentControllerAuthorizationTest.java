package com.fabricmanagement.finance.payment.api.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Correcting the ledger is not the same permission as entering a payment: voiding and reversing a
 * payment need {@code finance:reverse}, everything else keeps {@code finance:read}/{@code
 * finance:write}. A new endpoint must be classified here so it cannot silently inherit the wrong
 * permission.
 */
class PaymentControllerAuthorizationTest {

  private static final String READ = "@auth.can(authentication, 'finance', 'read')";
  private static final String WRITE = "@auth.can(authentication, 'finance', 'write')";
  private static final String REVERSE = "@auth.can(authentication, 'finance', 'reverse')";

  private static Map<String, String> mappedEndpoints() {
    Map<String, String> endpoints = new TreeMap<>();
    for (Method method : PaymentController.class.getDeclaredMethods()) {
      if (!Modifier.isPublic(method.getModifiers())
          || AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class) == null)
        continue;
      PreAuthorize guard = AnnotatedElementUtils.findMergedAnnotation(method, PreAuthorize.class);
      endpoints.put(method.getName(), guard == null ? null : guard.value());
    }
    return endpoints;
  }

  @Test
  void everyEndpointIsClassifiedAndGuarded() {
    assertThat(mappedEndpoints())
        .containsExactlyInAnyOrderEntriesOf(
            Map.of(
                "createPayment", WRITE,
                "getPayment", READ,
                "getPayments", READ,
                "allocatePayment", WRITE,
                "deallocatePayment", WRITE,
                "voidPayment", REVERSE,
                "reversePayment", REVERSE));
  }

  @Test
  void voidAndReversalAreNotImpliedByFinanceWrite() {
    Map<String, String> endpoints = mappedEndpoints();

    assertThat(endpoints.get("voidPayment")).isEqualTo(REVERSE).isNotEqualTo(WRITE);
    assertThat(endpoints.get("reversePayment")).isEqualTo(REVERSE).isNotEqualTo(WRITE);
  }

  @Test
  void onlyTheLedgerCorrectingEndpointsNeedTheReversePermission() {
    assertThat(
            mappedEndpoints().entrySet().stream()
                .filter(entry -> REVERSE.equals(entry.getValue()))
                .map(Map.Entry::getKey)
                .toList())
        .containsExactlyInAnyOrder("voidPayment", "reversePayment");
  }
}
