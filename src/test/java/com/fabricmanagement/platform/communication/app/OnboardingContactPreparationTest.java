package com.fabricmanagement.platform.communication.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.platform.common.exception.PlatformDomainException;
import com.fabricmanagement.platform.communication.domain.ContactType;
import com.fabricmanagement.platform.communication.infra.repository.ContactRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OnboardingContactPreparationTest {
  @Mock private ContactRepository contacts;
  @Mock private DomainEventPublisher events;
  @InjectMocks private ContactService service;

  @Test
  void britishLandlineIsNotClassifiedAsMobileBecauseItUsesE164() {
    var prepared = service.prepareOnboardingContact(" +44 (20) 7946-0123 ", null);
    assertThat(prepared.value()).isEqualTo("+442079460123");
    assertThat(prepared.type()).isEqualTo(ContactType.LANDLINE);
    verifyNoInteractions(contacts, events);
  }

  @Test
  void britishMobileRetainsItsTypeAndEmailIsCanonicalised() {
    assertThat(service.prepareOnboardingContact("+447400123456", null).type())
        .isEqualTo(ContactType.MOBILE);
    assertThat(
            service
                .prepareOnboardingContact(" Office@Hawthorn.Example ", ContactType.EMAIL)
                .value())
        .isEqualTo("office@hawthorn.example");
    assertThat(service.prepareOnboardingContact(null, null)).isNull();
    verifyNoInteractions(contacts, events);
  }

  @ParameterizedTest
  @ValueSource(strings = {"invalid", "02079460123", "+441", "+448001234567"})
  void invalidLocalAndUnsupportedTelephoneTypesFailBeforePersistence(String value) {
    assertThatThrownBy(() -> service.prepareOnboardingContact(value, null))
        .isInstanceOfSatisfying(
            PlatformDomainException.class,
            failure -> assertThat(failure.getErrorCode()).isEqualTo("ONBOARDING_PHONE_INVALID"));
    verifyNoInteractions(contacts, events);
  }
}
