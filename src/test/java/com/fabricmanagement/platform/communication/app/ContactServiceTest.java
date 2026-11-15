package com.fabricmanagement.platform.communication.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.exception.DomainException;
import com.fabricmanagement.platform.communication.domain.Contact;
import com.fabricmanagement.platform.communication.domain.ContactType;
import com.fabricmanagement.platform.communication.domain.event.ContactPointChangedEvent;
import com.fabricmanagement.platform.communication.infra.repository.ContactRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("ContactService")
class ContactServiceTest {

  private static final UUID TENANT_ID = UUID.randomUUID();

  @Mock private ContactRepository contactRepository;
  @Mock private DomainEventPublisher eventPublisher;

  @InjectMocks private ContactService service;

  @BeforeEach
  void setUp() {
    TenantContext.setCurrentTenantId(TENANT_ID);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Nested
  @DisplayName("createContact")
  class CreateContact {

    @Test
    void throwsWhenContactValueEmpty() {
      assertThatThrownBy(() -> service.createContact("  ", ContactType.EMAIL, null))
          .isInstanceOf(DomainException.class)
          .hasMessageContaining("cannot be empty");
    }

    @Test
    void throwsWhenInvalidEmailFormat() {
      assertThatThrownBy(() -> service.createContact("not-an-email", ContactType.EMAIL, null))
          .isInstanceOf(DomainException.class)
          .hasMessageContaining("Invalid email");
    }

    @Test
    void createsAndSavesContactWhenValidEmail() {
      when(contactRepository
              .findByTenantIdAndContactTypeAndContactValueIgnoreCaseOrderByCreatedAtAsc(
                  TENANT_ID, ContactType.EMAIL, "user@example.com"))
          .thenReturn(java.util.List.of());
      when(contactRepository.save(any(Contact.class))).thenAnswer(inv -> inv.getArgument(0));

      Contact result = service.createContact("  user@example.com  ", ContactType.EMAIL, null);

      ArgumentCaptor<Contact> captor = ArgumentCaptor.forClass(Contact.class);
      verify(contactRepository).save(captor.capture());
      Contact saved = captor.getValue();
      assertThat(saved.getContactValue()).isEqualTo("user@example.com");
      assertThat(saved.getContactType()).isEqualTo(ContactType.EMAIL);
      assertThat(result).isSameAs(saved);
    }

    @Test
    void returnsExistingContactWhenValueAndTypeMatch() {
      Contact existing =
          Contact.builder().contactValue("user@example.com").contactType(ContactType.EMAIL).build();
      existing.setId(UUID.randomUUID());
      existing.setTenantId(TENANT_ID);
      when(contactRepository
              .findByTenantIdAndContactTypeAndContactValueIgnoreCaseOrderByCreatedAtAsc(
                  TENANT_ID, ContactType.EMAIL, "user@example.com"))
          .thenReturn(java.util.List.of(existing));

      Contact result = service.createContact("user@example.com", ContactType.EMAIL, null);

      assertThat(result).isSameAs(existing);
      verify(contactRepository)
          .findByTenantIdAndContactTypeAndContactValueIgnoreCaseOrderByCreatedAtAsc(
              TENANT_ID, ContactType.EMAIL, "user@example.com");
    }
  }

  @Nested
  @DisplayName("findById")
  class FindById {

    @Test
    void returnsEmptyWhenContactBelongsToOtherTenant() {
      UUID contactId = UUID.randomUUID();
      Contact other = Contact.builder().build();
      other.setId(contactId);
      other.setTenantId(UUID.randomUUID());
      when(contactRepository.findById(contactId)).thenReturn(Optional.of(other));

      Optional<Contact> result = service.findById(contactId);

      assertThat(result).isEmpty();
    }

    @Test
    void returnsContactWhenSameTenant() {
      UUID contactId = UUID.randomUUID();
      Contact contact = Contact.builder().build();
      contact.setId(contactId);
      contact.setTenantId(TENANT_ID);
      when(contactRepository.findById(contactId)).thenReturn(Optional.of(contact));

      Optional<Contact> result = service.findById(contactId);

      assertThat(result).contains(contact);
    }
  }

  @Nested
  @DisplayName("findByValueAndType")
  class FindByValueAndType {

    @Test
    void returnsContactFromRepository() {
      Contact contact =
          Contact.builder().contactValue("user@example.com").contactType(ContactType.EMAIL).build();
      contact.setId(UUID.randomUUID());
      contact.setTenantId(TENANT_ID);
      when(contactRepository
              .findByTenantIdAndContactTypeAndContactValueIgnoreCaseOrderByCreatedAtAsc(
                  TENANT_ID, ContactType.EMAIL, "user@example.com"))
          .thenReturn(java.util.List.of(contact));

      Optional<Contact> result = service.findByValueAndType("user@example.com", ContactType.EMAIL);

      assertThat(result).contains(contact);
    }
  }

  @Nested
  @DisplayName("verifyContact")
  class VerifyContact {

    @Test
    void throwsWhenContactNotFound() {
      UUID contactId = UUID.randomUUID();
      when(contactRepository.findById(contactId)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> service.verifyContact(contactId))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("not found");
    }

    @Test
    void throwsWhenContactDifferentTenant() {
      UUID contactId = UUID.randomUUID();
      Contact contact = Contact.builder().build();
      contact.setId(contactId);
      contact.setTenantId(UUID.randomUUID());
      when(contactRepository.findById(contactId)).thenReturn(Optional.of(contact));

      assertThatThrownBy(() -> service.verifyContact(contactId))
          .isInstanceOf(DomainException.class)
          .hasMessageContaining("current tenant");
    }

    @Test
    void marksContactVerifiedAndSaves() {
      UUID contactId = UUID.randomUUID();
      Contact contact =
          Contact.builder()
              .contactValue("user@example.com")
              .contactType(ContactType.EMAIL)
              .isVerified(false)
              .build();
      contact.setId(contactId);
      contact.setTenantId(TENANT_ID);
      when(contactRepository.findById(contactId)).thenReturn(Optional.of(contact));
      when(contactRepository.save(any(Contact.class))).thenAnswer(inv -> inv.getArgument(0));

      Contact result = service.verifyContact(contactId);

      assertThat(contact.getIsVerified()).isTrue();
      verify(contactRepository).save(contact);
      assertThat(result).isSameAs(contact);
    }
  }

  @Nested
  @DisplayName("deleteContact")
  class DeleteContact {

    @Test
    void softDeletesContact() {
      UUID contactId = UUID.randomUUID();
      Contact contact = Contact.builder().build();
      contact.setId(contactId);
      contact.setTenantId(TENANT_ID);
      when(contactRepository.findById(contactId)).thenReturn(Optional.of(contact));
      when(contactRepository.save(any(Contact.class))).thenAnswer(inv -> inv.getArgument(0));

      service.deleteContact(contactId);

      verify(contactRepository).save(contact);
      assertThat(contact.getIsActive()).isFalse();
      ArgumentCaptor<ContactPointChangedEvent> event =
          ArgumentCaptor.forClass(ContactPointChangedEvent.class);
      verify(eventPublisher).publish(event.capture());
      assertThat(event.getValue().getContactId()).isEqualTo(contactId);
      assertThat(event.getValue().getChange()).isEqualTo(ContactPointChangedEvent.DELETED);
    }
  }

  @Nested
  @DisplayName("replacePoint")
  class ReplacePoint {

    private Contact jane(UUID contactId) {
      Contact contact =
          Contact.builder()
              .contactValue("jane@example.com")
              .contactType(ContactType.EMAIL)
              .isVerified(false)
              .build();
      contact.setId(contactId);
      contact.setTenantId(TENANT_ID);
      return contact;
    }

    @Test
    void anotherAddressIsReportedInTheSameTransaction() {
      UUID contactId = UUID.randomUUID();
      Contact contact = jane(contactId);
      contact.verify();
      when(contactRepository.save(contact)).thenReturn(contact);

      service.replacePoint(
          contact, service.prepareChange(contact, "jane.smith@elsewhere.example", null));

      assertThat(contact.getIsVerified()).isFalse();

      ArgumentCaptor<ContactPointChangedEvent> event =
          ArgumentCaptor.forClass(ContactPointChangedEvent.class);
      verify(eventPublisher).publish(event.capture());
      assertThat(event.getValue().getTenantId()).isEqualTo(TENANT_ID);
      assertThat(event.getValue().getContactId()).isEqualTo(contactId);
      assertThat(event.getValue().getChange()).isEqualTo(ContactPointChangedEvent.ADDRESS_CHANGED);
    }

    @Test
    void theSameMailboxInOtherLetterCaseIsNotReported() {
      UUID contactId = UUID.randomUUID();
      Contact contact = jane(contactId);
      contact.verify();

      service.replacePoint(contact, service.prepareChange(contact, "Jane@Example.com", null));

      assertThat(contact.getIsVerified()).isTrue();

      verify(eventPublisher, never()).publish(any());
    }
  }
}
