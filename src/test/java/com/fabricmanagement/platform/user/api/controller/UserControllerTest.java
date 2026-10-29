package com.fabricmanagement.platform.user.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.security.PermissionEvaluator;
import com.fabricmanagement.common.infrastructure.security.dto.PermissionResult;
import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.platform.common.exception.PlatformDomainException;
import com.fabricmanagement.platform.communication.app.ContactSuggestionService;
import com.fabricmanagement.platform.subscription.app.UserCreationOptionsService;
import com.fabricmanagement.platform.tenant.app.TenantService;
import com.fabricmanagement.platform.tenant.domain.TenantStatus;
import com.fabricmanagement.platform.tenant.dto.TenantDto;
import com.fabricmanagement.platform.user.app.UserLocaleService;
import com.fabricmanagement.platform.user.app.UserService;
import com.fabricmanagement.platform.user.domain.DataScope;
import com.fabricmanagement.platform.user.domain.port.EmployeeCreationPort;
import com.fabricmanagement.platform.user.dto.CurrentUserResponse;
import com.fabricmanagement.platform.user.dto.UpdateUserLanguagePreferenceRequest;
import com.fabricmanagement.platform.user.dto.UpdateUserTimezonePreferenceRequest;
import com.fabricmanagement.platform.user.dto.UserDto;
import com.fabricmanagement.platform.user.infra.repository.UserDepartmentRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

  private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
  private static final Instant TRIAL_ENDS_AT = Instant.parse("2026-09-26T12:00:00Z");

  @Mock private UserService userService;
  @Mock private ContactSuggestionService contactSuggestionService;
  @Mock private EmployeeCreationPort employeeCreationPort;
  @Mock private UserCreationOptionsService userCreationOptionsService;
  @Mock private UserDepartmentRepository userDepartmentRepository;
  @Mock private UserLocaleService userLocaleService;
  @Mock private PermissionEvaluator permissionEvaluator;
  @Mock private TenantService tenantService;
  @Mock private com.fabricmanagement.common.infrastructure.tenant.PackageAccessPort packageAccess;

  private UserController controller;

  @BeforeEach
  void setUp() {
    controller =
        new UserController(
            userService,
            contactSuggestionService,
            employeeCreationPort,
            userCreationOptionsService,
            userDepartmentRepository,
            userLocaleService,
            permissionEvaluator,
            tenantService,
            packageAccess);
    TenantContext.setCurrentTenantId(TENANT_ID);
    TenantContext.setCurrentUserId(USER_ID);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void getCurrentUserIncludesDemoTrialTenantContext() {
    stubCurrentUser();
    when(tenantService.getMyTenant())
        .thenReturn(
            TenantDto.builder()
                .id(TENANT_ID)
                .demoMode(true)
                .status(TenantStatus.TRIAL)
                .trialEndsAt(TRIAL_ENDS_AT)
                .build());

    ResponseEntity<ApiResponse<CurrentUserResponse>> response = controller.getCurrentUser();

    CurrentUserResponse body = response.getBody().getData();
    assertThat(body.getId()).isEqualTo(USER_ID);
    assertThat(body.getTenant().isDemoMode()).isTrue();
    assertThat(body.getTenant().getStatus()).isEqualTo(TenantStatus.TRIAL);
    assertThat(body.getTenant().getTrialEndsAt()).isEqualTo(TRIAL_ENDS_AT);
  }

  @Test
  void getCurrentUserIncludesActiveRealTenantContext() {
    stubCurrentUser();
    when(tenantService.getMyTenant())
        .thenReturn(
            TenantDto.builder()
                .id(TENANT_ID)
                .demoMode(false)
                .status(TenantStatus.ACTIVE)
                .trialEndsAt(null)
                .build());

    ResponseEntity<ApiResponse<CurrentUserResponse>> response = controller.getCurrentUser();

    CurrentUserResponse body = response.getBody().getData();
    assertThat(body.getTenant().isDemoMode()).isFalse();
    assertThat(body.getTenant().getStatus()).isEqualTo(TenantStatus.ACTIVE);
    assertThat(body.getTenant().getTrialEndsAt()).isNull();
  }

  @Test
  void getCurrentUserReportsTheResourcesTheTenantPackagesDoNotOpen() {
    stubCurrentUser();
    when(tenantService.getMyTenant())
        .thenReturn(TenantDto.builder().id(TENANT_ID).status(TenantStatus.ACTIVE).build());
    when(packageAccess.closedResources(TENANT_ID)).thenReturn(java.util.Set.of("yarn"));

    CurrentUserResponse body = controller.getCurrentUser().getBody().getData();

    assertThat(body.getClosedResources()).containsExactly("yarn");
  }

  @Test
  void languageChangeUsesAuthenticatedUserAndReturnsPreservedTimezone() {
    var request = new UpdateUserLanguagePreferenceRequest("tr");
    UserDto saved =
        UserDto.builder().id(USER_ID).preferredLocale("tr").preferredTimezone("UTC").build();
    when(userLocaleService.updateLanguagePreference(USER_ID, request)).thenReturn(saved);
    var response = controller.updateMyLanguagePreference(request);
    assertThat(response.getBody().getData().getPreferredTimezone()).isEqualTo("UTC");
    verify(userLocaleService).updateLanguagePreference(USER_ID, request);
  }

  @Test
  void languageChangeRejectsMissingAuthenticatedUser() {
    TenantContext.clear();
    assertThatThrownBy(
            () ->
                controller.updateMyLanguagePreference(
                    new UpdateUserLanguagePreferenceRequest("en-GB")))
        .isInstanceOf(PlatformDomainException.class);
    verifyNoInteractions(userLocaleService);
  }

  @Test
  void timezoneChangeUsesAuthenticatedUserAndReturnsPreservedLanguage() {
    var request = new UpdateUserTimezonePreferenceRequest("UTC");
    var saved =
        UserDto.builder().id(USER_ID).preferredLocale("tr").preferredTimezone("UTC").build();
    when(userLocaleService.updateTimezonePreference(USER_ID, request)).thenReturn(saved);
    var response = controller.updateMyTimezonePreference(request);
    assertThat(response.getBody().getData().getPreferredLocale()).isEqualTo("tr");
    verify(userLocaleService).updateTimezonePreference(USER_ID, request);
  }

  @Test
  void timezoneChangeRejectsMissingAuthenticatedUser() {
    TenantContext.clear();
    assertThatThrownBy(
            () ->
                controller.updateMyTimezonePreference(
                    new UpdateUserTimezonePreferenceRequest("UTC")))
        .isInstanceOf(PlatformDomainException.class);
    verifyNoInteractions(userLocaleService);
  }

  private void stubCurrentUser() {
    UserDto user =
        UserDto.builder()
            .id(USER_ID)
            .tenantId(TENANT_ID)
            .firstName("Ada")
            .lastName("Lovelace")
            .roleCode("ADMIN")
            .departmentCodes(List.of("ADMIN"))
            .build();
    PermissionResult permissions =
        new PermissionResult(Map.of("members", Map.of("read", DataScope.GLOBAL)), false);
    when(userService.findByIdWithPermissionData(TENANT_ID, USER_ID)).thenReturn(Optional.of(user));
    when(permissionEvaluator.evaluate(TENANT_ID, "ADMIN", List.of("ADMIN"), USER_ID))
        .thenReturn(permissions);
  }
}
