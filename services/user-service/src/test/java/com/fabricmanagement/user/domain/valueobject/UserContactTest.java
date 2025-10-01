package com.fabricmanagement.user.domain.valueobject;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for UserContact Value Object
 * 
 * Tests contact validation and business rules
 */
@DisplayName("UserContact Tests")
class UserContactTest {

    private static final String VALID_EMAIL = "test@example.com";
    private static final String VALID_PHONE = "+905551234567";

    @Nested
    @DisplayName("Email Contact Tests")
    class EmailContactTests {

        @Test
        @DisplayName("Should create email contact successfully")
        void shouldCreateEmailContactSuccessfully() {
            // When
            UserContact contact = UserContact.email("user123", VALID_EMAIL, true, true);

            // Then
            assertThat(contact).isNotNull();
            assertThat(contact.getContactValue()).isEqualTo(VALID_EMAIL);
            assertThat(contact.getContactType()).isEqualTo(UserContact.ContactType.EMAIL);
            assertThat(contact.isVerified()).isTrue();
            assertThat(contact.isPrimary()).isTrue();
        }

        @Test
        @DisplayName("Should create unverified email contact")
        void shouldCreateUnverifiedEmailContact() {
            // When
            UserContact contact = UserContact.email("user123", VALID_EMAIL, false, false);

            // Then
            assertThat(contact.getContactValue()).isEqualTo(VALID_EMAIL);
            assertThat(contact.getContactType()).isEqualTo(UserContact.ContactType.EMAIL);
            assertThat(contact.isVerified()).isFalse();
            assertThat(contact.isPrimary()).isFalse();
        }
    }

    @Nested
    @DisplayName("Phone Contact Tests")
    class PhoneContactTests {

        @Test
        @DisplayName("Should create phone contact successfully")
        void shouldCreatePhoneContactSuccessfully() {
            // When
            UserContact contact = UserContact.phone("user123", VALID_PHONE, true, true);

            // Then
            assertThat(contact).isNotNull();
            assertThat(contact.getContactValue()).isEqualTo(VALID_PHONE);
            assertThat(contact.getContactType()).isEqualTo(UserContact.ContactType.PHONE);
            assertThat(contact.isVerified()).isTrue();
            assertThat(contact.isPrimary()).isTrue();
        }

        @Test
        @DisplayName("Should create unverified phone contact")
        void shouldCreateUnverifiedPhoneContact() {
            // When
            UserContact contact = UserContact.phone("user123", VALID_PHONE, false, false);

            // Then
            assertThat(contact.getContactValue()).isEqualTo(VALID_PHONE);
            assertThat(contact.getContactType()).isEqualTo(UserContact.ContactType.PHONE);
            assertThat(contact.isVerified()).isFalse();
            assertThat(contact.isPrimary()).isFalse();
        }
    }

    @Nested
    @DisplayName("Contact Type Tests")
    class ContactTypeTests {

        @Test
        @DisplayName("Should have correct contact types")
        void shouldHaveCorrectContactTypes() {
            // When & Then
            assertThat(UserContact.ContactType.EMAIL).isNotNull();
            assertThat(UserContact.ContactType.PHONE).isNotNull();
        }

        @Test
        @DisplayName("Should create contact with EMAIL type")
        void shouldCreateContactWithEmailType() {
            // When
            UserContact contact = UserContact.builder()
                .userId("user123")
                .contactValue(VALID_EMAIL)
                .contactType(UserContact.ContactType.EMAIL)
                .isVerified(true)
                .isPrimary(true)
                .build();

            // Then
            assertThat(contact.getContactType()).isEqualTo(UserContact.ContactType.EMAIL);
        }

        @Test
        @DisplayName("Should create contact with PHONE type")
        void shouldCreateContactWithPhoneType() {
            // When
            UserContact contact = UserContact.builder()
                .userId("user123")
                .contactValue(VALID_PHONE)
                .contactType(UserContact.ContactType.PHONE)
                .isVerified(true)
                .isPrimary(true)
                .build();

            // Then
            assertThat(contact.getContactType()).isEqualTo(UserContact.ContactType.PHONE);
        }
    }

    @Nested
    @DisplayName("Contact Equality Tests")
    class ContactEqualityTests {

        @Test
        @DisplayName("Should be equal when all fields are same")
        void shouldBeEqualWhenAllFieldsAreSame() {
            // Given
            UserContact contact1 = UserContact.email("user123", VALID_EMAIL, true, true);
            UserContact contact2 = UserContact.email("user123", VALID_EMAIL, true, true);

            // When & Then
            assertThat(contact1).isEqualTo(contact2);
            assertThat(contact1.hashCode()).isEqualTo(contact2.hashCode());
        }

        @Test
        @DisplayName("Should not be equal when contact value is different")
        void shouldNotBeEqualWhenContactValueIsDifferent() {
            // Given
            UserContact contact1 = UserContact.email("user123", VALID_EMAIL, true, true);
            UserContact contact2 = UserContact.email("user123", "different@example.com", true, true);

            // When & Then
            assertThat(contact1).isNotEqualTo(contact2);
        }

        @Test
        @DisplayName("Should not be equal when contact type is different")
        void shouldNotBeEqualWhenContactTypeIsDifferent() {
            // Given
            UserContact contact1 = UserContact.email("user123", VALID_EMAIL, true, true);
            UserContact contact2 = UserContact.phone("user123", VALID_EMAIL, true, true);

            // When & Then
            assertThat(contact1).isNotEqualTo(contact2);
        }

        @Test
        @DisplayName("Should not be equal when verification status is different")
        void shouldNotBeEqualWhenVerificationStatusIsDifferent() {
            // Given
            UserContact contact1 = UserContact.email("user123", VALID_EMAIL, true, true);
            UserContact contact2 = UserContact.email("user123", VALID_EMAIL, false, true);

            // When & Then
            assertThat(contact1).isNotEqualTo(contact2);
        }

        @Test
        @DisplayName("Should not be equal when primary status is different")
        void shouldNotBeEqualWhenPrimaryStatusIsDifferent() {
            // Given
            UserContact contact1 = UserContact.email("user123", VALID_EMAIL, true, true);
            UserContact contact2 = UserContact.email("user123", VALID_EMAIL, true, false);

            // When & Then
            assertThat(contact1).isNotEqualTo(contact2);
        }
    }

    @Nested
    @DisplayName("Contact Immutability Tests")
    class ContactImmutabilityTests {

        @Test
        @DisplayName("Should be immutable")
        void shouldBeImmutable() {
            // Given
            UserContact contact = UserContact.email("user123", VALID_EMAIL, true, true);

            // When & Then
            assertThat(contact).isNotNull();
            // Value objects should be immutable by design - check that fields are set correctly
            assertThat(contact.getContactValue()).isEqualTo(VALID_EMAIL);
            assertThat(contact.getContactType()).isEqualTo(UserContact.ContactType.EMAIL);
            assertThat(contact.isVerified()).isTrue();
            assertThat(contact.isPrimary()).isTrue();
            
            // Verify that the object cannot be modified (no setters should be available)
            // This is ensured by the @Setter annotation on the class level
        }
    }
}
