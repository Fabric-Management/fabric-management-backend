package com.fabricmanagement.sales.quote.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.fabricmanagement.platform.user.domain.DataScope;
import com.fabricmanagement.sales.common.app.SalesAccessScopeResolver;
import com.fabricmanagement.sales.common.app.SalesAccessScopeResolver.AccessScope;
import com.fabricmanagement.sales.common.app.SalesAccessScopeResolver.PermissionFreshness;
import com.fabricmanagement.sales.quote.domain.Quote;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class QuoteAccessPolicyTest {

  private static final UUID TENANT = UUID.randomUUID();
  private static final UUID USER = UUID.randomUUID();
  private static final UUID CREATOR = UUID.randomUUID();
  private static final UUID ASSIGNEE = UUID.randomUUID();

  @Mock private SalesAccessScopeResolver resolver;

  @Test
  void ownScopeAcceptsCreatorOrPersistedAssignee() {
    QuoteAccessPolicy policy = new QuoteAccessPolicy(resolver);
    when(resolver.resolve(TENANT, USER, "read", PermissionFreshness.CACHED))
        .thenReturn(new AccessScope(DataScope.OWN, Set.of(USER)));

    assertThat(policy.canRead(TENANT, USER, quote(USER, ASSIGNEE))).isTrue();
    assertThat(policy.canRead(TENANT, USER, quote(CREATOR, USER))).isTrue();
    assertThat(policy.canRead(TENANT, USER, quote(CREATOR, ASSIGNEE))).isFalse();
  }

  @Test
  void departmentAndTenantBoundariesApplyToBothPrincipals() {
    QuoteAccessPolicy policy = new QuoteAccessPolicy(resolver);
    when(resolver.resolve(TENANT, USER, "write", PermissionFreshness.CACHED))
        .thenReturn(new AccessScope(DataScope.DEPARTMENT, Set.of(USER, ASSIGNEE)));

    assertThat(policy.canWrite(TENANT, USER, quote(CREATOR, ASSIGNEE))).isTrue();
    Quote otherTenant = quote(USER, ASSIGNEE);
    otherTenant.setTenantId(UUID.randomUUID());
    assertThat(policy.canWrite(TENANT, USER, otherTenant)).isFalse();
  }

  @Test
  void readableIdsUsesOneResolvedScopeAcrossQuotes() {
    QuoteAccessPolicy policy = new QuoteAccessPolicy(resolver);
    when(resolver.resolve(TENANT, USER, "read", PermissionFreshness.CACHED))
        .thenReturn(new AccessScope(DataScope.OWN, Set.of(USER)));
    Quote own = quote(USER, ASSIGNEE);
    Quote assigned = quote(CREATOR, USER);
    Quote outsider = quote(CREATOR, ASSIGNEE);

    assertThat(policy.readableQuoteIds(TENANT, USER, List.of(own, assigned, outsider)))
        .containsExactlyInAnyOrder(own.getId(), assigned.getId());
  }

  @Test
  void readAndWriteScopesAreIndependentAndMissingPrincipalsDenyNarrowScopes() {
    QuoteAccessPolicy policy = new QuoteAccessPolicy(resolver);
    when(resolver.resolve(TENANT, USER, "read", PermissionFreshness.CACHED))
        .thenReturn(new AccessScope(DataScope.OWN, Set.of(USER)));
    when(resolver.resolve(TENANT, USER, "write", PermissionFreshness.CACHED))
        .thenReturn(new AccessScope(DataScope.ORGANIZATION, Set.of()));
    Quote outsider = quote(CREATOR, ASSIGNEE);

    assertThat(policy.canRead(TENANT, USER, outsider)).isFalse();
    assertThat(policy.canWrite(TENANT, USER, outsider)).isTrue();
    assertThat(policy.canRead(TENANT, USER, quote(null, null))).isFalse();
  }

  private Quote quote(UUID creator, UUID assignee) {
    Quote quote = new Quote();
    quote.setId(UUID.randomUUID());
    quote.setTenantId(TENANT);
    quote.setCreatedBy(creator);
    quote.setAssignedToId(assignee);
    return quote;
  }
}
