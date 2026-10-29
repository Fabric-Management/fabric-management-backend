package com.fabricmanagement.architecture;

import static org.assertj.core.api.Assertions.*;

import com.fabricmanagement.finance.collection.api.controller.CollectionReportController;
import java.lang.reflect.Modifier;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

class CollectionReportArchTest {
  @Test
  void reportsRequireCurrentFinanceAuthorityAndNoSalesPermission() {
    for (var method : CollectionReportController.class.getDeclaredMethods()) {
      if (!Modifier.isPublic(method.getModifiers())) continue;
      assertThat(method.getAnnotation(PreAuthorize.class)).isNotNull();
      assertThat(method.getAnnotation(PreAuthorize.class).value())
          .isEqualTo("@auth.can(authentication,'finance','read')");
    }
  }
}
