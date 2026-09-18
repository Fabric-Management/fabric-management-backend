package com.fabricmanagement.platform.user.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.security.PermissionEvaluator;
import com.fabricmanagement.platform.user.domain.EmployeeSnapshot;
import com.fabricmanagement.platform.user.domain.User;
import com.fabricmanagement.platform.user.domain.port.EmployeeProjectionPort;
import com.fabricmanagement.platform.user.dto.UserDto;
import com.fabricmanagement.platform.user.infra.repository.UserRepository;
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
@DisplayName("UserQueryService")
class UserQueryServiceTest {

  @Mock private UserRepository userRepository;
  @Mock private EmployeeProjectionPort employeeProjectionPort;
  @Mock private UserWorkLocationService userWorkLocationService;
  @Mock private PermissionEvaluator permissionEvaluator;

  @InjectMocks private UserQueryService service;

  private final UUID tenantId = UUID.randomUUID();
  private final UUID userId = UUID.randomUUID();

  @Test
  @DisplayName("findById returns enriched UserDto")
  void findById_Enriched() {
    User user = User.builder().firstName("John").build();
    user.setId(userId);
    user.setTenantId(tenantId);
    EmployeeSnapshot snapshot =
        new EmployeeSnapshot(userId, null, null, null, null, "EMP-1", null, null);

    when(userRepository.findByTenantIdAndId(tenantId, userId)).thenReturn(Optional.of(user));
    when(employeeProjectionPort.findByUserId(user.getTenantId(), user.getId()))
        .thenReturn(Optional.of(snapshot));
    when(userWorkLocationService.getPrimaryLocationLabels(eq(tenantId), anyList()))
        .thenReturn(Map.of(userId, "Main Office"));

    Optional<UserDto> result = service.findById(tenantId, userId);

    assertThat(result).isPresent();
    assertThat(result.get().getFirstName()).isEqualTo("John");
    assertThat(result.get().getIsEmployee()).isTrue();
    assertThat(result.get().getEmployeeNumber()).isEqualTo("EMP-1");
    assertThat(result.get().getWorkLocationLabel()).isEqualTo("Main Office");
  }

  @Test
  @DisplayName("findByTenant uses batch enrichment to avoid N+1")
  void findByTenant_BatchEnriched() {
    User user1 = User.builder().firstName("U1").build();
    user1.setId(userId);
    List<User> users = List.of(user1);

    EmployeeSnapshot snapshot1 =
        new EmployeeSnapshot(userId, null, null, null, null, "E1", null, null);

    when(userRepository.findByTenantIdAndIsActiveTrue(tenantId)).thenReturn(users);
    when(employeeProjectionPort.findByUserIds(eq(tenantId), anyList()))
        .thenReturn(Map.of(userId, snapshot1));
    when(userWorkLocationService.getPrimaryLocationLabels(eq(tenantId), anyList()))
        .thenReturn(Map.of(userId, "Loc1"));

    List<UserDto> result = service.findByTenant(tenantId);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getIsEmployee()).isTrue();
    assertThat(result.get(0).getWorkLocationLabel()).isEqualTo("Loc1");

    verify(employeeProjectionPort).findByUserIds(eq(tenantId), anyList());
  }

  @Test
  @DisplayName("exists delegates to repository")
  void exists_Delegation() {
    when(userRepository.existsByTenantIdAndId(tenantId, userId)).thenReturn(true);
    assertThat(service.exists(tenantId, userId)).isTrue();
  }

  @Test
  void permissionIdentityPreservesPersistedDepartmentCodeCase() {
    User user = User.create("Mixed", "Case", UUID.randomUUID());
    user.setId(userId);
    user.setTenantId(tenantId);
    var department =
        com.fabricmanagement.platform.organization.domain.Department.create(
            user.getOrganizationId(), "Quality", "QUALITY-mixed", "Case-sensitive key");
    department.setId(UUID.randomUUID());
    user.getUserDepartments()
        .add(
            com.fabricmanagement.platform.user.domain.UserDepartment.builder()
                .tenantId(tenantId)
                .userId(userId)
                .departmentId(department.getId())
                .user(user)
                .department(department)
                .isActive(true)
                .isPrimary(true)
                .build());
    when(userRepository.findByIdWithPermissionData(tenantId, userId)).thenReturn(Optional.of(user));

    assertThat(service.findPermissionIdentity(tenantId, userId))
        .hasValueSatisfying(
            identity -> assertThat(identity.departmentCodes()).containsExactly("QUALITY-mixed"));
  }
}
