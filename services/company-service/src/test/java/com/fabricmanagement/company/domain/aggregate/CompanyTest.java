package com.fabricmanagement.company.domain.aggregate;

import com.fabricmanagement.company.domain.event.CompanyCreatedEvent;
import com.fabricmanagement.company.domain.event.CompanyUpdatedEvent;
import com.fabricmanagement.company.domain.event.CompanyDeletedEvent;
import com.fabricmanagement.company.domain.valueobject.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for Company Aggregate Root
 *
 * Tests all business logic and domain rules
 */
@DisplayName("Company Aggregate Tests")
class CompanyTest {

    private static final UUID TEST_TENANT_ID = UUID.randomUUID();
    private static final String TEST_COMPANY_NAME = "Test Company Inc";
    private static final String TEST_LEGAL_NAME = "Test Company Incorporated";

    @Nested
    @DisplayName("Company Creation Tests")
    class CompanyCreationTests {

        @Test
        @DisplayName("Should create company with valid data")
        void shouldCreateCompanyWithValidData() {
            // Given
            CompanyName name = new CompanyName(TEST_COMPANY_NAME);

            // When
            Company company = Company.create(
                TEST_TENANT_ID,
                name,
                TEST_LEGAL_NAME,
                CompanyType.LLC,
                Industry.TECHNOLOGY,
                "A test company"
            );

            // Then
            assertThat(company).isNotNull();
            assertThat(company.getTenantId()).isEqualTo(TEST_TENANT_ID);
            assertThat(company.getName().getValue()).isEqualTo(TEST_COMPANY_NAME);
            assertThat(company.getLegalName()).isEqualTo(TEST_LEGAL_NAME);
            assertThat(company.getType()).isEqualTo(CompanyType.LLC);
            assertThat(company.getIndustry()).isEqualTo(Industry.TECHNOLOGY);
            assertThat(company.getStatus()).isEqualTo(CompanyStatus.ACTIVE);
            assertThat(company.isActive()).isTrue();
            assertThat(company.getMaxUsers()).isEqualTo(10);
            assertThat(company.getCurrentUsers()).isEqualTo(0);
        }

        @Test
        @DisplayName("Should publish CompanyCreatedEvent when company is created")
        void shouldPublishCompanyCreatedEvent() {
            // Given
            CompanyName name = new CompanyName(TEST_COMPANY_NAME);

            // When
            Company company = Company.create(
                TEST_TENANT_ID,
                name,
                TEST_LEGAL_NAME,
                CompanyType.LLC,
                Industry.TECHNOLOGY,
                "A test company"
            );

            // Then
            List<Object> events = company.getAndClearDomainEvents();
            assertThat(events).hasSize(1);
            assertThat(events.get(0)).isInstanceOf(CompanyCreatedEvent.class);

            CompanyCreatedEvent event = (CompanyCreatedEvent) events.get(0);
            assertThat(event.getCompanyId()).isEqualTo(company.getId());
            assertThat(event.getTenantId()).isEqualTo(TEST_TENANT_ID.toString());
            assertThat(event.getCompanyName()).isEqualTo(TEST_COMPANY_NAME);
        }

        @Test
        @DisplayName("Should throw exception when tenant ID is null")
        void shouldThrowExceptionWhenTenantIdIsNull() {
            // Given
            CompanyName name = new CompanyName(TEST_COMPANY_NAME);

            // When & Then
            assertThatThrownBy(() -> Company.create(
                null,
                name,
                TEST_LEGAL_NAME,
                CompanyType.LLC,
                Industry.TECHNOLOGY,
                "A test company"
            ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Tenant ID cannot be null");
        }

        @Test
        @DisplayName("Should throw exception when company name is null")
        void shouldThrowExceptionWhenCompanyNameIsNull() {
            // When & Then
            assertThatThrownBy(() -> Company.create(
                TEST_TENANT_ID,
                null,
                TEST_LEGAL_NAME,
                CompanyType.LLC,
                Industry.TECHNOLOGY,
                "A test company"
            ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Company name cannot be null");
        }

        @Test
        @DisplayName("Should throw exception when company type is null")
        void shouldThrowExceptionWhenCompanyTypeIsNull() {
            // Given
            CompanyName name = new CompanyName(TEST_COMPANY_NAME);

            // When & Then
            assertThatThrownBy(() -> Company.create(
                TEST_TENANT_ID,
                name,
                TEST_LEGAL_NAME,
                null,
                Industry.TECHNOLOGY,
                "A test company"
            ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Company type cannot be null");
        }

        @Test
        @DisplayName("Should throw exception when industry is null")
        void shouldThrowExceptionWhenIndustryIsNull() {
            // Given
            CompanyName name = new CompanyName(TEST_COMPANY_NAME);

            // When & Then
            assertThatThrownBy(() -> Company.create(
                TEST_TENANT_ID,
                name,
                TEST_LEGAL_NAME,
                CompanyType.LLC,
                null,
                "A test company"
            ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Industry cannot be null");
        }
    }

    @Nested
    @DisplayName("Company Update Tests")
    class CompanyUpdateTests {

        @Test
        @DisplayName("Should update company information successfully")
        void shouldUpdateCompanyInformation() {
            // Given
            Company company = createTestCompany();
            company.getAndClearDomainEvents(); // Clear creation event

            // When
            company.updateCompany("New Legal Name", "Updated description", "http://newwebsite.com");

            // Then
            assertThat(company.getLegalName()).isEqualTo("New Legal Name");
            assertThat(company.getDescription()).isEqualTo("Updated description");
            assertThat(company.getWebsite()).isEqualTo("http://newwebsite.com");

            List<Object> events = company.getAndClearDomainEvents();
            assertThat(events).hasSize(1);
            assertThat(events.get(0)).isInstanceOf(CompanyUpdatedEvent.class);
        }

        @Test
        @DisplayName("Should update company settings successfully")
        void shouldUpdateCompanySettings() {
            // Given
            Company company = createTestCompany();
            company.getAndClearDomainEvents();

            java.util.Map<String, Object> settings = new java.util.HashMap<>();
            settings.put("theme", "dark");
            settings.put("notifications", true);

            // When
            company.updateSettings(settings);

            // Then
            assertThat(company.getSettings()).isEqualTo(settings);
            assertThat(company.getSettings().get("theme")).isEqualTo("dark");

            List<Object> events = company.getAndClearDomainEvents();
            assertThat(events).hasSize(1);
            assertThat(events.get(0)).isInstanceOf(CompanyUpdatedEvent.class);
        }

        @Test
        @DisplayName("Should update company logo successfully")
        void shouldUpdateCompanyLogo() {
            // Given
            Company company = createTestCompany();
            company.getAndClearDomainEvents();

            // When
            company.updateLogo("http://cdn.example.com/logo.png");

            // Then
            assertThat(company.getLogoUrl()).isEqualTo("http://cdn.example.com/logo.png");

            List<Object> events = company.getAndClearDomainEvents();
            assertThat(events).hasSize(1);
        }
    }

    @Nested
    @DisplayName("Company Status Tests")
    class CompanyStatusTests {

        @Test
        @DisplayName("Should activate company successfully")
        void shouldActivateCompany() {
            // Given
            Company company = createTestCompany();
            company.deactivate();
            company.getAndClearDomainEvents();

            // When
            company.activate();

            // Then
            assertThat(company.getStatus()).isEqualTo(CompanyStatus.ACTIVE);
            assertThat(company.isActive()).isTrue();
            assertThat(company.isActive()).isTrue();
        }

        @Test
        @DisplayName("Should deactivate company successfully")
        void shouldDeactivateCompany() {
            // Given
            Company company = createTestCompany();
            company.getAndClearDomainEvents();

            // When
            company.deactivate();

            // Then
            assertThat(company.getStatus()).isEqualTo(CompanyStatus.INACTIVE);
            assertThat(company.isActive()).isFalse();
            assertThat(company.isActive()).isFalse();
        }

        @Test
        @DisplayName("Should suspend company successfully")
        void shouldSuspendCompany() {
            // Given
            Company company = createTestCompany();
            company.getAndClearDomainEvents();

            // When
            company.suspend();

            // Then
            assertThat(company.getStatus()).isEqualTo(CompanyStatus.SUSPENDED);
            assertThat(company.isActive()).isFalse();
        }

        @Test
        @DisplayName("Should not be active when deleted")
        void shouldNotBeActiveWhenDeleted() {
            // Given
            Company company = createTestCompany();

            // When
            company.markAsDeleted();

            // Then
            assertThat(company.isActive()).isFalse();
            assertThat(company.getStatus()).isEqualTo(CompanyStatus.DELETED);

            List<Object> events = company.getAndClearDomainEvents();
            assertThat(events.stream().anyMatch(e -> e instanceof CompanyDeletedEvent)).isTrue();
        }
    }

    @Nested
    @DisplayName("User Management Tests")
    class UserManagementTests {

        @Test
        @DisplayName("Should add user successfully")
        void shouldAddUserSuccessfully() {
            // Given
            Company company = createTestCompany();
            int initialUserCount = company.getCurrentUsers();

            // When
            company.addUser();

            // Then
            assertThat(company.getCurrentUsers()).isEqualTo(initialUserCount + 1);
        }

        @Test
        @DisplayName("Should remove user successfully")
        void shouldRemoveUserSuccessfully() {
            // Given
            Company company = createTestCompany();
            company.addUser();
            int currentUsers = company.getCurrentUsers();

            // When
            company.removeUser();

            // Then
            assertThat(company.getCurrentUsers()).isEqualTo(currentUsers - 1);
        }

        @Test
        @DisplayName("Should throw exception when max users limit reached")
        void shouldThrowExceptionWhenMaxUsersReached() {
            // Given
            Company company = createTestCompany();
            int maxUsers = company.getMaxUsers();

            // Add users up to max
            for (int i = 0; i < maxUsers; i++) {
                company.addUser();
            }

            // When & Then
            assertThatThrownBy(() -> company.addUser())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Maximum user limit reached");
        }

        @Test
        @DisplayName("Should throw exception when removing user from empty company")
        void shouldThrowExceptionWhenRemovingFromEmpty() {
            // Given
            Company company = createTestCompany();

            // When & Then
            assertThatThrownBy(() -> company.removeUser())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("No users to remove");
        }

        @Test
        @DisplayName("Should check if company can add user")
        void shouldCheckIfCanAddUser() {
            // Given
            Company company = createTestCompany();

            // When & Then
            assertThat(company.canAddUser()).isTrue();

            // Add users up to max
            int maxUsers = company.getMaxUsers();
            for (int i = 0; i < maxUsers; i++) {
                company.addUser();
            }

            assertThat(company.canAddUser()).isFalse();
        }
    }

    @Nested
    @DisplayName("Subscription Management Tests")
    class SubscriptionManagementTests {

        @Test
        @DisplayName("Should update subscription successfully")
        void shouldUpdateSubscription() {
            // Given
            Company company = createTestCompany();
            company.getAndClearDomainEvents();

            LocalDateTime newEndDate = LocalDateTime.now().plusYears(2);

            // When
            company.updateSubscription("PREMIUM", 50, newEndDate);

            // Then
            assertThat(company.getSubscriptionPlan()).isEqualTo("PREMIUM");
            assertThat(company.getMaxUsers()).isEqualTo(50);
            assertThat(company.getSubscriptionEndDate()).isEqualTo(newEndDate);
        }

        @Test
        @DisplayName("Should check if subscription is active")
        void shouldCheckIfSubscriptionIsActive() {
            // Given
            Company company = createTestCompany();

            // When & Then
            assertThat(company.isSubscriptionActive()).isTrue();
        }

        @Test
        @DisplayName("Should detect expired subscription")
        void shouldDetectExpiredSubscription() {
            // Given
            Company company = createTestCompany();
            LocalDateTime pastDate = LocalDateTime.now().minusDays(1);
            company.updateSubscription("BASIC", 10, pastDate);

            // When & Then
            assertThat(company.isSubscriptionActive()).isFalse();
        }
    }

    // Helper method
    private Company createTestCompany() {
        CompanyName name = new CompanyName(TEST_COMPANY_NAME);
        return Company.create(
            TEST_TENANT_ID,
            name,
            TEST_LEGAL_NAME,
            CompanyType.LLC,
            Industry.TECHNOLOGY,
            "A test company"
        );
    }
}
