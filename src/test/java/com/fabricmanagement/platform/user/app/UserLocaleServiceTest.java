package com.fabricmanagement.platform.user.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.platform.user.domain.User;
import com.fabricmanagement.platform.user.domain.port.UserLocaleConfigPort;
import com.fabricmanagement.platform.user.dto.UpdateUserLanguagePreferenceRequest;
import com.fabricmanagement.platform.user.dto.UpdateUserTimezonePreferenceRequest;
import com.fabricmanagement.platform.user.dto.UserDto;
import com.fabricmanagement.platform.user.infra.repository.UserRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

@ExtendWith(MockitoExtension.class)
class UserLocaleServiceTest {
  private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
  @Mock private UserRepository userRepository;
  @Mock private UserQueryService userQueryService;
  @Mock private UserLocaleConfigPort userLocaleConfigPort;
  @InjectMocks private UserLocaleService service;

  @BeforeEach
  void setUp() {
    TenantContext.setCurrentTenantId(TENANT_ID);
    TenantContext.setCurrentUserId(USER_ID);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void languageChangePreservesTimezoneSavedFromAnotherDevice() {
    User user = userWithTimezone("UTC");
    UserDto result = stubSave(user);
    assertThat(
            service.updateLanguagePreference(
                USER_ID, new UpdateUserLanguagePreferenceRequest("tr")))
        .isSameAs(result);
    assertThat(user.getPreferredLocale()).isEqualTo("tr");
    assertThat(user.getPreferredTimezone()).isEqualTo("UTC");
    verify(userLocaleConfigPort).saveOrUpdate(TENANT_ID, USER_ID, "TR", "UTC");
  }

  @Test
  void languageChangePreservesAnUnsetTimezoneRatherThanInventingOne() {
    User user = userWithTimezone(null);
    stubSave(user);
    service.updateLanguagePreference(USER_ID, new UpdateUserLanguagePreferenceRequest("en-GB"));
    assertThat(user.getPreferredTimezone()).isNull();
    verify(userLocaleConfigPort).saveOrUpdate(TENANT_ID, USER_ID, "EN", null);
  }

  @Test
  void languageChangeCannotFindAnotherTenantsUser() {
    when(userRepository.findByTenantIdAndId(TENANT_ID, USER_ID)).thenReturn(Optional.empty());
    assertThatThrownBy(
            () ->
                service.updateLanguagePreference(
                    USER_ID, new UpdateUserLanguagePreferenceRequest("en-GB")))
        .isInstanceOf(NotFoundException.class);
    verifyNoInteractions(userLocaleConfigPort, userQueryService);
    verify(userRepository, never()).save(any());
  }

  @Test
  void concurrentPreferenceWriteIsNotSilentlyAccepted() {
    User user = userWithTimezone("UTC");
    when(userRepository.findByTenantIdAndId(TENANT_ID, USER_ID)).thenReturn(Optional.of(user));
    when(userRepository.save(user))
        .thenThrow(new ObjectOptimisticLockingFailureException(User.class, USER_ID));
    assertThatThrownBy(
            () ->
                service.updateLanguagePreference(
                    USER_ID, new UpdateUserLanguagePreferenceRequest("tr")))
        .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    verifyNoInteractions(userLocaleConfigPort, userQueryService);
  }

  @Test
  void timezoneChangePreservesLanguageSavedFromAnotherDevice() {
    User user = userWithTimezone("Europe/London");
    user.updateLanguagePreference("tr");
    stubSave(user);
    service.updateTimezonePreference(USER_ID, new UpdateUserTimezonePreferenceRequest("UTC"));
    assertThat(user.getPreferredLocale()).isEqualTo("tr");
    assertThat(user.getPreferredTimezone()).isEqualTo("UTC");
    verify(userLocaleConfigPort).saveOrUpdate(TENANT_ID, USER_ID, "TR", "UTC");
  }

  @Test
  void timezoneChangePreservesUnsetLanguage() {
    User user = userWithTimezone("Europe/London");
    user.updateLanguagePreference(null);
    stubSave(user);
    service.updateTimezonePreference(USER_ID, new UpdateUserTimezonePreferenceRequest("UTC"));
    assertThat(user.getPreferredLocale()).isNull();
    verify(userLocaleConfigPort).saveOrUpdate(TENANT_ID, USER_ID, null, "UTC");
  }

  @Test
  void timezoneChangeIsTenantScoped() {
    when(userRepository.findByTenantIdAndId(TENANT_ID, USER_ID)).thenReturn(Optional.empty());
    assertThatThrownBy(
            () ->
                service.updateTimezonePreference(
                    USER_ID, new UpdateUserTimezonePreferenceRequest("UTC")))
        .isInstanceOf(NotFoundException.class);
    verify(userRepository, never()).save(any());
    verifyNoInteractions(userLocaleConfigPort, userQueryService);
  }

  private User userWithTimezone(String timezone) {
    User user = User.builder().firstName("Harriet").lastName("Bennett").build();
    user.setId(USER_ID);
    user.setTenantId(TENANT_ID);
    user.updateLocalePreferences("en-GB", timezone);
    return user;
  }

  private UserDto stubSave(User user) {
    when(userRepository.findByTenantIdAndId(TENANT_ID, USER_ID)).thenReturn(Optional.of(user));
    when(userRepository.save(user)).thenReturn(user);
    UserDto result = UserDto.builder().id(USER_ID).tenantId(TENANT_ID).build();
    when(userQueryService.findById(TENANT_ID, USER_ID)).thenReturn(Optional.of(result));
    return result;
  }
}
