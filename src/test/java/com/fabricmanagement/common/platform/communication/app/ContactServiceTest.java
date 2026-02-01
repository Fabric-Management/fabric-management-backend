package com.fabricmanagement.common.platform.communication.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.exception.DomainException;
import com.fabricmanagement.common.platform.communication.domain.Contact;
import com.fabricmanagement.common.platform.communication.domain.ContactType;
import com.fabricmanagement.common.platform.communication.infra.repository.ContactRepository;
import java.util.List;
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
      assertThatThrownBy(() -> service.createContact("  ", ContactType.EMAIL, "Label", false, null))
          .isInstanceOf(DomainException.class)
          .hasMessageContaining("cannot be empty");
    }

    @Test
    void throwsWhenInvalidEmailFormat() {
      assertThatThrownBy(
              () -> service.createContact("not-an-email", ContactType.EMAIL, "Label", false, null))
          .isInstanceOf(DomainException.class)
          .hasMessageContaining("Invalid email");
    }

    @Test
    void createsAndSavesContactWhenValidEmail() {
      when(contactRepository.findByTenantIdAndContactValueAndContactType(
              TENANT_ID, "user@example.com", ContactType.EMAIL))
          .thenReturn(Optional.empty());
      when(contactRepository.save(any(Contact.class))).thenAnswer(inv -> inv.getArgument(0));

      Contact result =
          service.createContact(
              "  user@example.com  ", ContactType.EMAIL, "Work Email", false, null);

      ArgumentCaptor<Contact> captor = ArgumentCaptor.forClass(Contact.class);
      verify(contactRepository).save(captor.capture());
      Contact saved = captor.getValue();
      assertThat(saved.getContactValue()).isEqualTo("user@example.com");
      assertThat(saved.getContactType()).isEqualTo(ContactType.EMAIL);
      assertThat(saved.getLabel()).isEqualTo("Work Email");
      assertThat(saved.getIsPersonal()).isFalse();
      assertThat(result).isSameAs(saved);
    }

    @Test
    void returnsExistingContactWhenValueAndTypeMatch() {
      Contact existing =
          Contact.builder().contactValue("user@example.com").contactType(ContactType.EMAIL).build();
      existing.setId(UUID.randomUUID());
      existing.setTenantId(TENANT_ID);
      when(contactRepository.findByTenantIdAndContactValueAndContactType(
              TENANT_ID, "user@example.com", ContactType.EMAIL))
          .thenReturn(Optional.of(existing));

      Contact result =
          service.createContact("user@example.com", ContactType.EMAIL, "Label", false, null);

      assertThat(result).isSameAs(existing);
      verify(contactRepository)
          .findByTenantIdAndContactValueAndContactType(
              TENANT_ID, "user@example.com", ContactType.EMAIL);
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
      when(contactRepository.findByTenantIdAndContactValueAndContactType(
              TENANT_ID, "user@example.com", ContactType.EMAIL))
          .thenReturn(Optional.of(contact));

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
  @DisplayName("setAsPrimary")
  class SetAsPrimary {

    @Test
    void removesPrimaryFromOthersAndSetsContactPrimary() {
      UUID contactId = UUID.randomUUID();
      Contact contact = Contact.builder().contactType(ContactType.EMAIL).isPrimary(false).build();
      contact.setId(contactId);
      contact.setTenantId(TENANT_ID);
      Contact otherPrimary =
          Contact.builder().contactType(ContactType.EMAIL).isPrimary(true).build();
      otherPrimary.setId(UUID.randomUUID());
      otherPrimary.setTenantId(TENANT_ID);
      when(contactRepository.findById(contactId)).thenReturn(Optional.of(contact));
      when(contactRepository.findByTenantIdAndContactType(TENANT_ID, ContactType.EMAIL))
          .thenReturn(List.of(contact, otherPrimary));
      when(contactRepository.save(any(Contact.class))).thenAnswer(inv -> inv.getArgument(0));

      Contact result = service.setAsPrimary(contactId);

      verify(contactRepository).save(otherPrimary);
      verify(contactRepository).save(contact);
      assertThat(otherPrimary.getIsPrimary()).isFalse();
      assertThat(contact.getIsPrimary()).isTrue();
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
    }
  }
}
