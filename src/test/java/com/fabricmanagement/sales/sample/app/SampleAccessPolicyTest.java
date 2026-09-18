package com.fabricmanagement.sales.sample.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.fabricmanagement.platform.user.domain.DataScope;
import com.fabricmanagement.sales.common.app.SalesAccessScopeResolver;
import com.fabricmanagement.sales.common.app.SalesAccessScopeResolver.AccessScope;
import com.fabricmanagement.sales.common.app.SalesAccessScopeResolver.PermissionFreshness;
import com.fabricmanagement.sales.sample.domain.SampleRequest;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SampleAccessPolicyTest {

  private static final UUID TENANT = UUID.randomUUID();
  private static final UUID USER = UUID.randomUUID();
  private static final UUID COLLEAGUE = UUID.randomUUID();

  @Mock private SalesAccessScopeResolver resolver;

  @Test
  void requestCreatorIsTheOnlyObjectPrincipal() {
    SampleAccessPolicy policy = new SampleAccessPolicy(resolver);
    when(resolver.resolve(TENANT, USER, "read", PermissionFreshness.CACHED))
        .thenReturn(new AccessScope(DataScope.OWN, Set.of(USER)));

    assertThat(policy.canRead(TENANT, USER, request(USER))).isTrue();
    assertThat(policy.canRead(TENANT, USER, request(COLLEAGUE))).isFalse();
  }

  @Test
  void writeScopeIsIndependentAndTenantBound() {
    SampleAccessPolicy policy = new SampleAccessPolicy(resolver);
    when(resolver.resolve(TENANT, USER, "write", PermissionFreshness.CACHED))
        .thenReturn(new AccessScope(DataScope.ORGANIZATION, Set.of()));

    assertThat(policy.canWrite(TENANT, USER, request(COLLEAGUE))).isTrue();
    SampleRequest otherTenant = request(USER);
    otherTenant.setTenantId(UUID.randomUUID());
    assertThat(policy.canWrite(TENANT, USER, otherTenant)).isFalse();
  }

  @Test
  void departmentScopeAcceptsAColleagueButRejectsAMissingCreator() {
    SampleAccessPolicy policy = new SampleAccessPolicy(resolver);
    when(resolver.resolve(TENANT, USER, "read", PermissionFreshness.CACHED))
        .thenReturn(new AccessScope(DataScope.DEPARTMENT, Set.of(USER, COLLEAGUE)));

    assertThat(policy.canRead(TENANT, USER, request(COLLEAGUE))).isTrue();
    assertThat(policy.canRead(TENANT, USER, request(null))).isFalse();
  }

  private SampleRequest request(UUID creator) {
    SampleRequest request = new SampleRequest();
    request.setTenantId(TENANT);
    request.setCreatedBy(creator);
    return request;
  }
}
