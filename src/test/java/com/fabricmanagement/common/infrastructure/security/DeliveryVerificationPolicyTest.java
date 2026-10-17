package com.fabricmanagement.common.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.domain.vo.DeliveryVerificationStatus;
import com.fabricmanagement.common.domain.vo.Verification;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.platform.user.domain.DataScope;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

/** CONSIGNEE-1 C5/C6: only a holder of sales:verify-delivery creates things already verified. */
@ExtendWith(MockitoExtension.class)
class DeliveryVerificationPolicyTest {

  private static final UUID USER = UUID.randomUUID();

  @Mock private DataScopeGuard scopeGuard;

  private DeliveryVerificationPolicy policy;

  @BeforeEach
  void setUp() {
    policy = new DeliveryVerificationPolicy(scopeGuard);
    TenantContext.setCurrentUserId(USER);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void aHolderOfThePermissionMayVerifyAndWhatTheyCreateIsVerifiedByThem() {
    when(scopeGuard.currentScope("sales", "verify-delivery")).thenReturn(DataScope.DEPARTMENT);

    assertThat(policy.actorMayVerify()).isTrue();
    Verification created = policy.forCurrentActor();
    assertThat(created.getStatus()).isEqualTo(DeliveryVerificationStatus.VERIFIED);
    assertThat(created.getVerifiedBy()).isEqualTo(USER);
    assertThat(created.getVerifiedAt()).isNotNull();
  }

  @Test
  void aUserWithoutThePermissionMayNotVerifyAndWhatTheyCreateWaits() {
    when(scopeGuard.currentScope("sales", "verify-delivery")).thenReturn(null);

    assertThat(policy.actorMayVerify()).isFalse();
    Verification created = policy.forCurrentActor();
    assertThat(created.getStatus()).isEqualTo(DeliveryVerificationStatus.UNVERIFIED);
    assertThat(created.getVerifiedBy()).isNull();
    assertThat(created.getVerifiedAt()).isNull();
  }

  @Test
  void withNoAuthenticatedUserTheAnswerIsNo() {
    when(scopeGuard.currentScope("sales", "verify-delivery"))
        .thenThrow(new AccessDeniedException("no user"));

    assertThat(policy.actorMayVerify()).isFalse();
    assertThat(policy.forCurrentActor().getStatus())
        .isEqualTo(DeliveryVerificationStatus.UNVERIFIED);
  }

  @Test
  void theSystemCountsAsAHolderBecauseTheGuardGivesItGlobalScope() {
    when(scopeGuard.currentScope("sales", "verify-delivery")).thenReturn(DataScope.GLOBAL);

    assertThat(policy.actorMayVerify()).isTrue();
  }
}
