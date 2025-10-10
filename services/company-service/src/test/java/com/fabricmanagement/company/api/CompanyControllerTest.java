package com.fabricmanagement.company.api;

import com.fabricmanagement.company.application.dto.CreateCompanyRequest;
import com.fabricmanagement.company.application.service.CompanyService;
import com.fabricmanagement.shared.application.context.SecurityContext;
import com.fabricmanagement.shared.application.response.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Company Controller Test - Pure Mockito
 * 
 * Tests company creation endpoint with controller logic:
 * - Successful company creation (CUSTOMER type)
 * - Controller delegates to service properly
 * - Response structure validation
 * 
 * Test Coverage: Controller layer (pure unit test, no Spring context)
 * 
 * Note: Validation testing done separately in integration tests
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Company Controller Tests - Pure Mockito")
class CompanyControllerTest {

    @Mock
    private CompanyService companyService;

    @InjectMocks
    private CompanyController companyController;

    private static final UUID TEST_COMPANY_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private static final UUID TEST_TENANT_ID = UUID.fromString("7c9e6679-7425-40de-963d-42a6ee08cd6c");
    private static final String TEST_USER_ID = "user-123";

    private SecurityContext mockSecurityContext;

    @BeforeEach
    void setUp() {
        mockSecurityContext = SecurityContext.builder()
            .userId(TEST_USER_ID)
            .tenantId(TEST_TENANT_ID)
            .roles(new String[]{"ADMIN"})
            .build();
    }

    @Test
    @DisplayName("Should create CUSTOMER company successfully")
    void shouldCreateCustomerCompanySuccessfully() {
        // Given: Valid customer company request
        CreateCompanyRequest request = CreateCompanyRequest.builder()
            .name("ABC Tekstil San. ve Tic. A.Ş.")
            .legalName("ABC Tekstil Sanayi ve Ticaret Anonim Şirketi")
            .taxId("1234567890")
            .registrationNumber("İST-2024-123456")
            .type("CORPORATION")
            .industry("MANUFACTURING")
            .description("30 yıldır tekstil sektöründe faaliyet gösteren müşteri firmamız")
            .website("https://www.abctekstil.com.tr")
            .logoUrl("https://www.abctekstil.com.tr/logo.png")
            .businessType("CUSTOMER")
            .parentCompanyId(null)
            .relationshipType("CUSTOMER")
            .build();

        when(companyService.createCompany(any(), any(), anyString()))
            .thenReturn(TEST_COMPANY_ID);

        // When
        ResponseEntity<ApiResponse<UUID>> response = companyController.createCompany(request, mockSecurityContext);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getSuccess()).isTrue();
        assertThat(response.getBody().getData()).isEqualTo(TEST_COMPANY_ID);
        assertThat(response.getBody().getMessage()).isEqualTo("Company created successfully");
    }

    @Test
    @DisplayName("Should create INTERNAL company (default businessType)")
    void shouldCreateInternalCompany() {
        // Given: Request without businessType (defaults to INTERNAL in handler)
        CreateCompanyRequest request = CreateCompanyRequest.builder()
            .name("Bizim Firma A.Ş.")
            .legalName("Bizim Firma Anonim Şirketi")
            .type("CORPORATION")
            .industry("MANUFACTURING")
            .description("Ana üretici firmamız")
            .build();

        when(companyService.createCompany(any(), any(), anyString()))
            .thenReturn(TEST_COMPANY_ID);

        // When
        ResponseEntity<ApiResponse<UUID>> response = companyController.createCompany(request, mockSecurityContext);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getSuccess()).isTrue();
        assertThat(response.getBody().getData()).isEqualTo(TEST_COMPANY_ID);
    }

    @Test
    @DisplayName("Should create company with minimal required fields")
    void shouldCreateCompanyWithMinimalFields() {
        // Given: Minimal valid request
        CreateCompanyRequest request = CreateCompanyRequest.builder()
            .name("Minimal Company")
            .type("LLC")
            .industry("RETAIL")
            .build();

        when(companyService.createCompany(any(), any(), anyString()))
            .thenReturn(TEST_COMPANY_ID);

        // When
        ResponseEntity<ApiResponse<UUID>> response = companyController.createCompany(request, mockSecurityContext);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getSuccess()).isTrue();
        assertThat(response.getBody().getData()).isEqualTo(TEST_COMPANY_ID);
    }

    @Test
    @DisplayName("Should create SUPPLIER company with parent relationship")
    void shouldCreateSupplierCompany() {
        // Given: SUPPLIER company with parent relationship
        UUID parentCompanyId = UUID.fromString("660e8400-e29b-41d4-a716-446655440001");
        
        CreateCompanyRequest request = CreateCompanyRequest.builder()
            .name("Tedarikçi Firma Ltd.")
            .legalName("Tedarikçi Firma Limited Şirketi")
            .type("LLC")
            .industry("MANUFACTURING")
            .businessType("SUPPLIER")
            .parentCompanyId(parentCompanyId.toString())
            .relationshipType("SUPPLIER")
            .build();

        when(companyService.createCompany(any(), any(), anyString()))
            .thenReturn(TEST_COMPANY_ID);

        // When
        ResponseEntity<ApiResponse<UUID>> response = companyController.createCompany(request, mockSecurityContext);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getSuccess()).isTrue();
        assertThat(response.getBody().getData()).isEqualTo(TEST_COMPANY_ID);
    }

    @Test
    @DisplayName("Should create company with all optional fields")
    void shouldCreateCompanyWithAllFields() {
        // Given: Request with all fields populated
        CreateCompanyRequest request = CreateCompanyRequest.builder()
            .name("Full Details Company")
            .legalName("Full Details Company Inc.")
            .taxId("9876543210")
            .registrationNumber("REG-2024-999")
            .type("CORPORATION")
            .industry("TECHNOLOGY")
            .description("Company with all details filled")
            .website("https://fulldetails.com")
            .logoUrl("https://fulldetails.com/logo.png")
            .businessType("CUSTOMER")
            .relationshipType("CUSTOMER")
            .build();

        when(companyService.createCompany(any(), any(), anyString()))
            .thenReturn(TEST_COMPANY_ID);

        // When
        ResponseEntity<ApiResponse<UUID>> response = companyController.createCompany(request, mockSecurityContext);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getSuccess()).isTrue();
        assertThat(response.getBody().getData()).isEqualTo(TEST_COMPANY_ID);
        assertThat(response.getBody().getMessage()).isEqualTo("Company created successfully");
    }

    @Test
    @DisplayName("Should delegate to service with correct parameters")
    void shouldDelegateToServiceWithCorrectParameters() {
        // Given: Valid request
        CreateCompanyRequest request = CreateCompanyRequest.builder()
            .name("Test Company")
            .type("LLC")
            .industry("RETAIL")
            .build();

        when(companyService.createCompany(request, TEST_TENANT_ID, TEST_USER_ID))
            .thenReturn(TEST_COMPANY_ID);

        // When
        ResponseEntity<ApiResponse<UUID>> response = companyController.createCompany(request, mockSecurityContext);

        // Then: Verify service was called with correct params
        assertThat(response).isNotNull();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData()).isEqualTo(TEST_COMPANY_ID);
    }

    @Test
    @DisplayName("Should return 201 CREATED status for successful creation")
    void shouldReturn201CreatedStatus() {
        // Given
        CreateCompanyRequest request = CreateCompanyRequest.builder()
            .name("Status Test Company")
            .type("CORPORATION")
            .industry("FINANCE")
            .build();

        when(companyService.createCompany(any(), any(), anyString()))
            .thenReturn(TEST_COMPANY_ID);

        // When
        ResponseEntity<ApiResponse<UUID>> response = companyController.createCompany(request, mockSecurityContext);

        // Then: Verify 201 status
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }
}
