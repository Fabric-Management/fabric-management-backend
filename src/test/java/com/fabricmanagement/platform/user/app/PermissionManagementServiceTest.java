package com.fabricmanagement.platform.user.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.security.PermissionEvaluator;
import com.fabricmanagement.common.infrastructure.security.PermissionKey;
import com.fabricmanagement.platform.common.exception.PlatformDomainException;
import com.fabricmanagement.platform.user.domain.DataScope;
import com.fabricmanagement.platform.user.domain.PermissionOverride;
import com.fabricmanagement.platform.user.domain.PermissionTemplate;
import com.fabricmanagement.platform.user.dto.CreatePermissionOverrideRequest;
import com.fabricmanagement.platform.user.dto.CreatePermissionTemplateRequest;
import com.fabricmanagement.platform.user.dto.UpdatePermissionTemplateRequest;
import com.fabricmanagement.platform.user.infra.repository.PermissionOverrideRepository;
import com.fabricmanagement.platform.user.infra.repository.PermissionTemplateRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PermissionManagementServiceTest {
  private static final UUID TENANT = UUID.fromString("11111111-1111-4111-8111-111111111111");
  private static final UUID USER = UUID.fromString("22222222-2222-4222-8222-222222222222");
  @Mock private PermissionTemplateRepository templates;
  @Mock private PermissionOverrideRepository overrides;
  @Mock private PermissionEvaluator evaluator;
  @Mock private UserQueryService users;
  @InjectMocks private PermissionManagementService service;

  @Test
  void everyCataloguedPairIsAcceptedByBothWriteBoundaries() {
    when(templates.save(any(PermissionTemplate.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(overrides.save(any(PermissionOverride.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    for (PermissionKey key : PermissionKey.values()) {
      var template = service.createTemplate(TENANT, templateRequest(key.resource(), key.action()));
      var override =
          service.createOverride(TENANT, USER, overrideRequest(key.resource(), key.action()));
      assertThat(template.getResource()).isEqualTo(key.resource());
      assertThat(template.getAction()).isEqualTo(key.action());
      assertThat(override.getResource()).isEqualTo(key.resource());
      assertThat(override.getAction()).isEqualTo(key.action());
    }
  }

  @ParameterizedTest
  @CsvSource({"sales,deliver", "colors,ship", "dashboard,cancel", "Sales,read", "sales,READ"})
  void invalidPairsAreRejectedBeforeAnyPersistence(String resource, String action) {
    assertThatThrownBy(() -> service.createTemplate(TENANT, templateRequest(resource, action)))
        .isInstanceOfSatisfying(
            PlatformDomainException.class,
            error -> {
              assertThat(error.getHttpStatus()).isEqualTo(400);
              assertThat(error.getErrorCode()).isEqualTo("BAD_REQUEST");
            })
        .hasMessageContaining(resource + ":" + action);
    assertThatThrownBy(
            () -> service.createOverride(TENANT, USER, overrideRequest(resource, action)))
        .isInstanceOf(PlatformDomainException.class)
        .hasMessageContaining(resource + ":" + action);
    verifyNoInteractions(templates, overrides);
  }

  @Test
  void revocationForAValidPairStillPreservesNullScope() {
    when(overrides.save(any(PermissionOverride.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    var request = overrideRequest("costing", "read");
    request.setDataScope(null);
    assertThat(service.createOverride(TENANT, USER, request).getDataScope()).isNull();
  }

  @ParameterizedTest
  @CsvSource({
    "admin,access",
    "dashboard,view",
    "fiber,approve",
    "flowboard,edit",
    "flowboard,manage",
    "flowboard,view",
    "notifications,view",
    "partners,read",
    "partners,write",
    "projects,manage",
    "projects,read",
    "projects,write",
    "reports,export",
    "reports,view",
    "settings,manage",
    "settings,view",
    "settings,write"
  })
  void retiredPairsCannotBeCreatedAsTemplatesOrOverrides(String resource, String action) {
    assertBadRequest(() -> service.createTemplate(TENANT, templateRequest(resource, action)));
    assertBadRequest(() -> service.createOverride(TENANT, USER, overrideRequest(resource, action)));
    verifyNoInteractions(templates, overrides);
  }

  @Test
  void retiredTemplateCannotBeReactivatedOrHaveItsScopeChanged() {
    PermissionTemplate template = storedTemplate("projects", "read");
    Instant deletedAt = Instant.parse("2026-09-01T12:00:00Z");
    template.setDeletedAt(deletedAt);
    template.setIsActive(false);
    when(templates.findById(template.getId())).thenReturn(Optional.of(template));

    assertThatThrownBy(() -> service.updateTemplate(TENANT, template.getId(), updateRequest()))
        .isInstanceOfSatisfying(
            PlatformDomainException.class,
            error -> {
              assertThat(error.getHttpStatus()).isEqualTo(410);
              assertThat(error.getErrorCode()).isEqualTo("GONE");
            });
    assertThat(template.getDeletedAt()).isEqualTo(deletedAt);
    assertThat(template.getIsActive()).isFalse();
    assertThat(template.getDataScope()).isEqualTo(DataScope.OWN);
    verify(templates, never()).save(any());
  }

  @Test
  void uncataloguedOpenTemplateCannotBeReactivated() {
    PermissionTemplate template = storedTemplate("projects", "read");
    template.setIsActive(false);
    when(templates.findById(template.getId())).thenReturn(Optional.of(template));

    assertBadRequest(() -> service.updateTemplate(TENANT, template.getId(), updateRequest()));
    assertThat(template.getIsActive()).isFalse();
    assertThat(template.getDataScope()).isEqualTo(DataScope.OWN);
    verify(templates, never()).save(any());
  }

  @Test
  void deletedCataloguedTemplateIsAlsoGone() {
    PermissionTemplate template = storedTemplate("sales", "read");
    template.delete();
    when(templates.findById(template.getId())).thenReturn(Optional.of(template));

    assertThatThrownBy(() -> service.updateTemplate(TENANT, template.getId(), updateRequest()))
        .isInstanceOfSatisfying(
            PlatformDomainException.class,
            error -> assertThat(error.getHttpStatus()).isEqualTo(410));
    verify(templates, never()).save(any());
  }

  @Test
  void inactiveButNotDeletedCataloguedTemplateCanStillBeEnabled() {
    PermissionTemplate template = storedTemplate("sales", "read");
    template.setIsActive(false);
    when(templates.findById(template.getId())).thenReturn(Optional.of(template));
    when(templates.save(template)).thenReturn(template);

    service.updateTemplate(TENANT, template.getId(), updateRequest());

    assertThat(template.getIsActive()).isTrue();
    assertThat(template.getDeletedAt()).isNull();
    assertThat(template.getDataScope()).isEqualTo(DataScope.ORGANIZATION);
    verify(templates).save(template);
  }

  @Test
  void anotherTenantsRetiredTemplateIsForbiddenBeforeItsStateIsDisclosed() {
    PermissionTemplate template = storedTemplate("projects", "read");
    template.setTenantId(USER);
    template.delete();
    when(templates.findById(template.getId())).thenReturn(Optional.of(template));

    assertThatThrownBy(() -> service.updateTemplate(TENANT, template.getId(), updateRequest()))
        .isInstanceOfSatisfying(
            PlatformDomainException.class,
            error -> assertThat(error.getHttpStatus()).isEqualTo(403));
    verify(templates, never()).save(any());
  }

  private PermissionTemplate storedTemplate(String resource, String action) {
    PermissionTemplate template =
        PermissionTemplate.builder()
            .roleCode("MANAGER")
            .resource(resource)
            .action(action)
            .dataScope(DataScope.OWN)
            .build();
    template.setId(UUID.randomUUID());
    template.setTenantId(TENANT);
    return template;
  }

  private UpdatePermissionTemplateRequest updateRequest() {
    var request = new UpdatePermissionTemplateRequest();
    request.setIsActive(true);
    request.setDataScope(DataScope.ORGANIZATION);
    return request;
  }

  private void assertBadRequest(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
    assertThatThrownBy(action)
        .isInstanceOfSatisfying(
            PlatformDomainException.class,
            error -> {
              assertThat(error.getHttpStatus()).isEqualTo(400);
              assertThat(error.getErrorCode()).isEqualTo("BAD_REQUEST");
            });
  }

  private CreatePermissionTemplateRequest templateRequest(String resource, String action) {
    var request = new CreatePermissionTemplateRequest();
    request.setRoleCode("MANAGER");
    request.setResource(resource);
    request.setAction(action);
    request.setDataScope(DataScope.ORGANIZATION);
    return request;
  }

  private CreatePermissionOverrideRequest overrideRequest(String resource, String action) {
    var request = new CreatePermissionOverrideRequest();
    request.setUserId(USER);
    request.setResource(resource);
    request.setAction(action);
    request.setDataScope(DataScope.OWN);
    return request;
  }
}
