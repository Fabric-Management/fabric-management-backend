package com.fabricmanagement.common.infrastructure.bootstrap;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.platform.tenant.app.TenantClonerService;
import com.fabricmanagement.platform.tenant.app.TenantSystemService;
import com.fabricmanagement.platform.tenant.dto.TenantDto;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class TenantSeederPropertyRegistryTest {

  @Mock private TenantSystemService tenantSystemService;
  @Mock private TenantClonerService tenantClonerService;
  @Mock private TransactionTemplate transactionTemplate;
  @Mock private DemoTransactionSeeder demoTransactionSeeder;

  private TenantSeeder tenantSeeder;

  @BeforeEach
  void setUp() {
    tenantSeeder =
        new TenantSeeder(
            tenantSystemService, tenantClonerService, transactionTemplate, demoTransactionSeeder);
  }

  @Test
  @SuppressWarnings("unchecked")
  void bootstrapSeederUsesReferenceClonePathThatProvisionsRegistryAndYarnCatalogues() {
    UUID tenantId = UUID.randomUUID();
    UUID templateTenantId = UUID.randomUUID();
    when(transactionTemplate.execute(any(TransactionCallback.class)))
        .thenAnswer(
            invocation -> {
              TransactionCallback<UUID> callback = invocation.getArgument(0);
              return callback.doInTransaction(mock(TransactionStatus.class));
            });
    when(tenantSystemService.createTenant(any()))
        .thenReturn(TenantDto.builder().id(tenantId).name("Nexus Fabrics").build());
    when(tenantClonerService.findTemplateTenantId()).thenReturn(templateTenantId);
    when(tenantClonerService.cloneRolesToTenant(templateTenantId, tenantId)).thenReturn(1);
    when(tenantClonerService.cloneReferenceDataToTenant(tenantId)).thenReturn(1);

    tenantSeeder.seed();

    verify(tenantClonerService).cloneReferenceDataToTenant(tenantId);
    verify(demoTransactionSeeder).seedIfEnabledFor(tenantId);
  }
}
