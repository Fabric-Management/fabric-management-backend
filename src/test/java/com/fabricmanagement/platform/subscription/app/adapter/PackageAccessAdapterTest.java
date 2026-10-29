package com.fabricmanagement.platform.subscription.app.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.tenant.TenantAccessPort;
import com.fabricmanagement.platform.subscription.domain.OSDefinition;
import com.fabricmanagement.platform.subscription.domain.Subscription;
import com.fabricmanagement.platform.subscription.infra.repository.OSDefinitionRepository;
import com.fabricmanagement.platform.subscription.infra.repository.SubscriptionRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** PACKAGE-ACCESS-1: a tenant's enabled packages decide which permission resources are open. */
@ExtendWith(MockitoExtension.class)
class PackageAccessAdapterTest {

  private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");

  @Mock private OSDefinitionRepository definitions;
  @Mock private SubscriptionRepository subscriptions;
  @Mock private TenantAccessPort tenantAccess;

  private PackageAccessAdapter adapter;

  @BeforeEach
  void setUp() {
    adapter = new PackageAccessAdapter(definitions, subscriptions, tenantAccess);
    lenient().when(tenantAccess.isWritable(TENANT)).thenReturn(true);
    lenient()
        .when(definitions.findByTenantId(TenantContext.SYSTEM_TENANT_ID))
        .thenReturn(
            List.of(
                definition("FabricOS", "sales", "products"),
                definition("YarnOS", "yarn"),
                definition("AccountOS", "finance"),
                definition("LoomOS")));
  }

  @Test
  void aPackageOpensExactlyTheResourcesItOwns() {
    when(subscriptions.findByTenantId(TENANT))
        .thenReturn(List.of(learning("FabricOS"), learning("YarnOS")));

    assertThat(adapter.isResourceOpen(TENANT, "sales")).isTrue();
    assertThat(adapter.isResourceOpen(TENANT, "yarn")).isTrue();
    assertThat(adapter.isResourceOpen(TENANT, "finance")).isFalse();
    assertThat(adapter.closedResources(TENANT)).containsExactly("finance");
  }

  @Test
  void deselectingAPackageClosesItsResourcesAndReselectingReopensThem() {
    Subscription yarn = learning("YarnOS");
    when(subscriptions.findByTenantId(TENANT)).thenReturn(List.of(learning("FabricOS"), yarn));
    assertThat(adapter.isResourceOpen(TENANT, "yarn")).isTrue();

    yarn.deselectEvaluation();
    assertThat(adapter.isResourceOpen(TENANT, "yarn")).isFalse();
    assertThat(adapter.closedResources(TENANT)).contains("yarn");

    yarn.selectEvaluation(true, null, "OWNER");
    assertThat(adapter.isResourceOpen(TENANT, "yarn")).isTrue();
  }

  @Test
  void aPassedRealTrialDeadlineClosesThePackageWithoutAnyJobRunning() {
    Subscription yarn = learning("YarnOS");
    yarn.setLearningAccess(false);
    yarn.setTrialEndsAt(Instant.now().minus(1, ChronoUnit.HOURS));
    when(subscriptions.findByTenantId(TENANT)).thenReturn(List.of(learning("FabricOS"), yarn));

    assertThat(adapter.isResourceOpen(TENANT, "yarn")).isFalse();
  }

  @Test
  void aCancelledPackageStaysClosed() {
    Subscription finance = learning("AccountOS");
    finance.cancel();
    when(subscriptions.findByTenantId(TENANT)).thenReturn(List.of(learning("FabricOS"), finance));

    assertThat(adapter.isResourceOpen(TENANT, "finance")).isFalse();
  }

  @Test
  void aResourceNoPackageClaimsStaysOpen() {
    when(subscriptions.findByTenantId(TENANT)).thenReturn(List.of(learning("FabricOS")));

    assertThat(adapter.isResourceOpen(TENANT, "organization")).isTrue();
  }

  @Test
  void aTenantWithoutAnySubscriptionIsNotGovernedByPackages() {
    when(subscriptions.findByTenantId(TENANT)).thenReturn(List.of());

    assertThat(adapter.isResourceOpen(TENANT, "finance")).isTrue();
    assertThat(adapter.closedResources(TENANT)).isEmpty();
  }

  @Test
  void aReadOnlyTenantKeepsReadAccessToItsData() {
    when(subscriptions.findByTenantId(TENANT)).thenReturn(List.of(learning("FabricOS")));
    when(tenantAccess.isWritable(TENANT)).thenReturn(false);

    assertThat(adapter.isResourceOpen(TENANT, "finance")).isTrue();
    assertThat(adapter.closedResources(TENANT)).isEmpty();
  }

  @Test
  void onlyTheSystemCatalogueClaimsResources() {
    when(subscriptions.findByTenantId(TENANT)).thenReturn(List.of(learning("FabricOS")));

    adapter.isResourceOpen(TENANT, "finance");
    adapter.closedResources(TENANT);

    // Tenant-private definitions are never asked for, so they can never claim a resource.
    verify(definitions, atLeastOnce()).findByTenantId(TenantContext.SYSTEM_TENANT_ID);
    verifyNoMoreInteractions(definitions);
  }

  @Test
  void aDeactivatedPackageDefinitionStillGuardsItsResources() {
    OSDefinition retired = definition("YarnOS", "yarn");
    retired.setIsActive(false);
    offerCatalogue(definition("FabricOS", "sales"), retired, definition("AccountOS", "finance"));

    // A tenant that never held the package is not let in because nothing offers it any more.
    when(subscriptions.findByTenantId(TENANT)).thenReturn(List.of(learning("FabricOS")));
    assertThat(adapter.isResourceOpen(TENANT, "yarn")).isFalse();
    assertThat(adapter.closedResources(TENANT)).contains("yarn", "finance");

    // A tenant that already holds it keeps using it.
    when(subscriptions.findByTenantId(TENANT))
        .thenReturn(List.of(learning("FabricOS"), learning("YarnOS")));
    assertThat(adapter.isResourceOpen(TENANT, "yarn")).isTrue();
    assertThat(adapter.closedResources(TENANT)).doesNotContain("yarn");
  }

  @Test
  void aSoftDeletedPackageDefinitionStillGuardsItsResources() {
    OSDefinition deleted = definition("YarnOS", "yarn");
    deleted.delete();
    offerCatalogue(definition("FabricOS", "sales"), deleted);
    when(subscriptions.findByTenantId(TENANT)).thenReturn(List.of(learning("FabricOS")));

    assertThat(adapter.isResourceOpen(TENANT, "yarn")).isFalse();
    assertThat(adapter.closedResources(TENANT)).containsExactly("yarn");
  }

  private void offerCatalogue(OSDefinition... catalogue) {
    when(definitions.findByTenantId(TenantContext.SYSTEM_TENANT_ID)).thenReturn(List.of(catalogue));
  }

  private static OSDefinition definition(String code, String... resources) {
    OSDefinition definition =
        OSDefinition.builder()
            .osCode(code)
            .osName(code)
            .includedModules(new java.util.ArrayList<>(List.of(resources)))
            .build();
    definition.setTenantId(TenantContext.SYSTEM_TENANT_ID);
    definition.setIsActive(true);
    return definition;
  }

  private static Subscription learning(String code) {
    Subscription subscription =
        Subscription.builder().osCode(code).osName(code).startDate(Instant.now()).build();
    subscription.setTenantId(TENANT);
    subscription.setIsActive(true);
    subscription.selectEvaluation(true, null, "SERVER_DEFAULT");
    return subscription;
  }
}
