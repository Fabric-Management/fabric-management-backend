package com.fabricmanagement.human.core.employee.app.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.human.core.employee.domain.Employee;
import com.fabricmanagement.human.core.employee.infra.repository.EmployeeRepository;
import com.fabricmanagement.platform.user.domain.EmployeeSnapshot;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("EmployeeProjectionAdapter")
class EmployeeProjectionAdapterTest {

  @Mock private EmployeeRepository employeeRepository;
  @InjectMocks private EmployeeProjectionAdapter adapter;

  @Test
  @DisplayName("findByUserId returns present snapshot when employee exists")
  void findByUserId_Success() {
    UUID tenantId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    Employee employee = Employee.builder().userId(userId).employeeNumber("E1").build();

    when(employeeRepository.findByTenantIdAndUserId(tenantId, userId))
        .thenReturn(Optional.of(employee));

    Optional<EmployeeSnapshot> result = adapter.findByUserId(tenantId, userId);

    assertThat(result).isPresent();
    assertThat(result.get().employeeNumber()).isEqualTo("E1");
    verify(employeeRepository).findByTenantIdAndUserId(tenantId, userId);
  }

  @Test
  @DisplayName("findByUserId returns empty when employee not found")
  void findByUserId_NotFound() {
    UUID tenantId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    when(employeeRepository.findByTenantIdAndUserId(tenantId, userId)).thenReturn(Optional.empty());

    Optional<EmployeeSnapshot> result = adapter.findByUserId(tenantId, userId);

    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("findByUserIds returns map of snapshots for existing employees")
  void findByUserIds_FiltersExisting() {
    UUID tenantId = UUID.randomUUID();
    UUID userId1 = UUID.randomUUID();
    UUID userId2 = UUID.randomUUID(); // missing
    List<UUID> userIds = List.of(userId1, userId2);

    Employee employee1 = Employee.builder().userId(userId1).employeeNumber("E1").build();
    when(employeeRepository.findByTenantIdAndUserIdIn(tenantId, userIds))
        .thenReturn(List.of(employee1));

    Map<UUID, EmployeeSnapshot> result = adapter.findByUserIds(tenantId, userIds);

    assertThat(result).hasSize(1);
    assertThat(result.get(userId1)).isNotNull();
    assertThat(result.get(userId1).employeeNumber()).isEqualTo("E1");
    assertThat(result.containsKey(userId2)).isFalse();
  }

  @Test
  @DisplayName("findByUserIds returns empty map for empty user list")
  void findByUserIds_EmptyList() {
    Map<UUID, EmployeeSnapshot> result =
        adapter.findByUserIds(UUID.randomUUID(), Collections.emptyList());
    assertThat(result).isEmpty();
  }
}
