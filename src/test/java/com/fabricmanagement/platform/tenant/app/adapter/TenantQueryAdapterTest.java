package com.fabricmanagement.platform.tenant.app.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.SystemTransactionExecutor;
import com.fabricmanagement.common.infrastructure.tenant.TenantReference;
import com.fabricmanagement.platform.tenant.app.TenantUnderCreation;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

@ExtendWith(MockitoExtension.class)
class TenantQueryAdapterTest {

  @Mock private SystemTransactionExecutor systemExecutor;
  @Mock private TenantUnderCreation tenantUnderCreation;
  @Mock private JdbcTemplate transactionJdbc;

  @Test
  void aTenantThisTransactionIsCreatingIsFoundOnTheTransactionsConnection() {
    UUID tenantId = UUID.randomUUID();
    TenantReference reference = new TenantReference(tenantId, "ACME-001", "Acme", "REGULAR");
    TenantQueryAdapter adapter = new TenantQueryAdapter(systemExecutor, tenantUnderCreation);
    when(tenantUnderCreation.isUnderCreation(tenantId)).thenReturn(true);
    when(tenantUnderCreation.primaryJdbc()).thenReturn(transactionJdbc);
    when(transactionJdbc.query(
            anyString(), ArgumentMatchers.<RowMapper<TenantReference>>any(), any(Object[].class)))
        .thenReturn(List.of(reference));

    assertThat(adapter.findById(tenantId)).contains(reference);
    // Its row is uncommitted: the system connection cannot see it.
    verifyNoInteractions(systemExecutor);
  }

  @Test
  void anyOtherTenantIsStillReadThroughTheSystemConnection() {
    UUID tenantId = UUID.randomUUID();
    TenantReference reference = new TenantReference(tenantId, "ACME-001", "Acme", "REGULAR");
    TenantQueryAdapter adapter = new TenantQueryAdapter(systemExecutor, tenantUnderCreation);
    when(systemExecutor.executeQueryForObject(
            anyString(), ArgumentMatchers.<RowMapper<TenantReference>>any(), any()))
        .thenReturn(reference);

    Optional<TenantReference> found = adapter.findById(tenantId);

    assertThat(found).contains(reference);
    verify(tenantUnderCreation).isUnderCreation(tenantId);
  }
}
