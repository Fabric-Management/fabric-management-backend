package com.fabricmanagement.common.platform.company.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.exception.TaxIdAlreadyExistsException;
import com.fabricmanagement.common.platform.company.domain.Company;
import com.fabricmanagement.common.platform.company.domain.CompanyType;
import com.fabricmanagement.common.platform.company.domain.event.CompanyCreatedEvent;
import com.fabricmanagement.common.platform.company.dto.CompanyDto;
import com.fabricmanagement.common.platform.company.dto.CreateCompanyRequest;
import com.fabricmanagement.common.platform.company.dto.CreateTenantCompanyRequest;
import com.fabricmanagement.common.platform.company.infra.repository.CompanyRepository;
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
@DisplayName("CompanyCreationService")
class CompanyCreationServiceTest {

  private static final UUID TENANT_ID = UUID.randomUUID();

  @Mock private CompanyRepository companyRepository;
  @Mock private CompanyHierarchyService hierarchyService;

  @Mock
  private com.fabricmanagement.common.platform.communication.app.ContactService contactService;

  @Mock
  private com.fabricmanagement.common.platform.communication.app.AddressService addressService;

  @Mock private CompanyContactAssignmentService companyContactAssignmentService;
  @Mock private CompanyAddressAssignmentService companyAddressAssignmentService;
  @Mock private DomainEventPublisher eventPublisher;

  @InjectMocks private CompanyCreationService service;

  @BeforeEach
  void setUp() {
    TenantContext.setCurrentTenantId(TENANT_ID);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Nested
  @DisplayName("createCompany")
  class CreateCompany {

    @Test
    void throwsWhenTenantContextNotSet() {
      TenantContext.clear();
      CreateCompanyRequest request =
          CreateCompanyRequest.builder()
              .companyName("ACME")
              .taxId("123")
              .companyType(CompanyType.VERTICAL_MILL)
              .build();

      assertThatThrownBy(() -> service.createCompany(request))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Tenant context must be set");
    }

    @Test
    void throwsWhenTaxIdAlreadyExistsInTenant() {
      CreateCompanyRequest request =
          CreateCompanyRequest.builder()
              .companyName("ACME")
              .taxId("123")
              .companyType(CompanyType.VERTICAL_MILL)
              .build();
      when(companyRepository.existsByTenantIdAndTaxId(TENANT_ID, "123")).thenReturn(true);

      assertThatThrownBy(() -> service.createCompany(request))
          .isInstanceOf(TaxIdAlreadyExistsException.class)
          .hasMessageContaining("tax ID already exists");
    }

    @Test
    void validatesParentWhenParentCompanyIdProvided() {
      UUID parentId = UUID.randomUUID();
      CreateCompanyRequest request =
          CreateCompanyRequest.builder()
              .companyName("Sub")
              .taxId("456")
              .companyType(CompanyType.FASON)
              .parentCompanyId(parentId)
              .build();
      when(companyRepository.existsByTenantIdAndTaxId(TENANT_ID, "456")).thenReturn(false);
      when(companyRepository.save(any(Company.class)))
          .thenAnswer(
              inv -> {
                Company c = inv.getArgument(0);
                c.setId(UUID.randomUUID());
                c.setTenantId(TENANT_ID);
                return c;
              });

      service.createCompany(request);

      verify(hierarchyService).validateParent(parentId);
    }

    @Test
    void savesCompanyAndPublishesEvent() {
      CreateCompanyRequest request =
          CreateCompanyRequest.builder()
              .companyName("ACME")
              .taxId("789")
              .companyType(CompanyType.SPINNER)
              .build();
      UUID companyId = UUID.randomUUID();
      when(companyRepository.existsByTenantIdAndTaxId(TENANT_ID, "789")).thenReturn(false);
      when(companyRepository.save(any(Company.class)))
          .thenAnswer(
              inv -> {
                Company c = inv.getArgument(0);
                c.setId(companyId);
                c.setTenantId(TENANT_ID);
                return c;
              });

      CompanyDto result = service.createCompany(request);

      ArgumentCaptor<Company> companyCaptor = ArgumentCaptor.forClass(Company.class);
      verify(companyRepository).save(companyCaptor.capture());
      Company saved = companyCaptor.getValue();
      assertThat(saved.getCompanyName()).isEqualTo("ACME");
      assertThat(saved.getTaxId()).isEqualTo("789");
      assertThat(saved.getCompanyType()).isEqualTo(CompanyType.SPINNER);

      ArgumentCaptor<CompanyCreatedEvent> eventCaptor =
          ArgumentCaptor.forClass(CompanyCreatedEvent.class);
      verify(eventPublisher).publish(eventCaptor.capture());
      CompanyCreatedEvent event = eventCaptor.getValue();
      assertThat(event.getTenantId()).isEqualTo(TENANT_ID);
      assertThat(event.getCompanyId()).isEqualTo(companyId);
      assertThat(event.getCompanyName()).isEqualTo("ACME");
      assertThat(event.getCompanyType()).isEqualTo("SPINNER");

      assertThat(result.getId()).isEqualTo(companyId);
      assertThat(result.getCompanyName()).isEqualTo("ACME");
    }
  }

  @Nested
  @DisplayName("createTenantCompany")
  class CreateTenantCompany {

    @Test
    void throwsWhenCompanyTypeIsNotTenant() {
      CreateTenantCompanyRequest request =
          CreateTenantCompanyRequest.builder()
              .companyName("ACME")
              .taxId("123")
              .companyType(CompanyType.FIBER_SUPPLIER)
              .build();

      assertThatThrownBy(() -> service.createTenantCompany(request))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Company type must be a tenant type");
    }

    @Test
    void throwsWhenTaxIdExistsGlobally() {
      CreateTenantCompanyRequest request =
          CreateTenantCompanyRequest.builder()
              .companyName("ACME")
              .taxId("123")
              .companyType(CompanyType.VERTICAL_MILL)
              .build();
      when(companyRepository.existsByTaxId("123")).thenReturn(true);

      assertThatThrownBy(() -> service.createTenantCompany(request))
          .isInstanceOf(TaxIdAlreadyExistsException.class)
          .hasMessageContaining("tax ID already exists");
    }

    @Test
    void savesTenantCompanyWithTenantIdEqualsIdAndPublishesEvent() {
      CreateTenantCompanyRequest request =
          CreateTenantCompanyRequest.builder()
              .companyName("Akka Yalilar")
              .taxId("999")
              .companyType(CompanyType.WEAVER)
              .build();
      when(companyRepository.existsByTaxId("999")).thenReturn(false);
      when(companyRepository.findByUid(any())).thenReturn(Optional.empty());
      UUID companyId = UUID.randomUUID();
      when(companyRepository.save(any(Company.class)))
          .thenAnswer(
              inv -> {
                Company c = inv.getArgument(0);
                if (c.getId() == null) {
                  c.setId(companyId);
                } else {
                  c.setTenantId(c.getId());
                }
                return c;
              });

      CompanyDto result = service.createTenantCompany(request);

      verify(companyRepository, times(2)).save(any(Company.class));
      ArgumentCaptor<CompanyCreatedEvent> eventCaptor =
          ArgumentCaptor.forClass(CompanyCreatedEvent.class);
      verify(eventPublisher).publish(eventCaptor.capture());
      CompanyCreatedEvent event = eventCaptor.getValue();
      assertThat(event.getTenantId()).isEqualTo(companyId);
      assertThat(event.getCompanyId()).isEqualTo(companyId);
      assertThat(result.getId()).isEqualTo(companyId);
    }
  }
}
