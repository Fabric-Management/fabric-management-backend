package com.fabricmanagement.product.yarn.app.backfill;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.SystemTransactionExecutor;
import com.fabricmanagement.product.yarn.app.bootstrap.YarnBlankDesignationRemediationRunner;
import com.fabricmanagement.product.yarn.app.bootstrap.YarnLegacyBackfillRunner;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;

@ExtendWith(MockitoExtension.class)
class YarnBlankDesignationRemediationRunnerTest {

  @Mock private YarnBlankDesignationRemediationService service;
  @Mock private SystemTransactionExecutor systemTransactions;

  @Test
  void eventMethodIsOrderedAt240AfterTheLegacyBackfill() throws Exception {
    when(systemTransactions.executeInTransaction(any())).thenReturn(List.of());

    new YarnBlankDesignationRemediationRunner(service, systemTransactions).run();

    var remediation = YarnBlankDesignationRemediationRunner.class.getDeclaredMethod("run");
    var legacy = YarnLegacyBackfillRunner.class.getDeclaredMethod("run");
    assertThat(remediation.getAnnotation(EventListener.class)).isNotNull();
    assertThat(remediation.getAnnotation(Order.class).value()).isEqualTo(240);
    assertThat(legacy.getAnnotation(Order.class).value()).isEqualTo(230);
  }
}
