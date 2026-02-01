package com.fabricmanagement.common.platform.company.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.platform.communication.domain.Address;
import com.fabricmanagement.common.platform.communication.infra.repository.AddressRepository;
import com.fabricmanagement.common.platform.company.domain.Company;
import com.fabricmanagement.common.platform.company.domain.CompanyAddress;
import com.fabricmanagement.common.platform.company.domain.CompanyType;
import com.fabricmanagement.common.platform.company.infra.repository.CompanyAddressRepository;
import com.fabricmanagement.common.platform.company.infra.repository.CompanyRepository;
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

/**
 * Tests {@link CompanyAddressAssignmentService} (and thus {@link
 * com.fabricmanagement.common.infrastructure.assignment.BaseAssignmentService}): assign, unassign,
 * setPrimary, getPrimary, getByParent.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CompanyAddressAssignmentService (BaseAssignmentService)")
class CompanyAddressAssignmentServiceTest {

  private static final UUID TENANT_ID = UUID.randomUUID();
  private static final UUID COMPANY_ID = UUID.randomUUID();
  private static final UUID ADDRESS_ID = UUID.randomUUID();
  private static final UUID ADDRESS_ID_2 = UUID.randomUUID();

  @Mock private CompanyRepository companyRepository;
  @Mock private AddressRepository addressRepository;
  @Mock private CompanyAddressRepository companyAddressRepository;
  @Mock private DomainEventPublisher eventPublisher;

  @InjectMocks private CompanyAddressAssignmentService service;

  @BeforeEach
  void setUp() {
    TenantContext.setCurrentTenantId(TENANT_ID);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  private Company company(UUID id) {
    Company c = new Company();
    c.setId(id);
    c.setTenantId(TENANT_ID);
    c.setCompanyName("Company");
    c.setTaxId("tax");
    c.setCompanyType(CompanyType.SPINNER);
    c.setIsActive(true);
    return c;
  }

  private Address address(UUID id) {
    Address a = new Address();
    a.setId(id);
    a.setTenantId(TENANT_ID);
    a.setStreetAddress("Street");
    a.setCity("City");
    a.setCountry("Country");
    return a;
  }

  private CompanyAddress junction(UUID companyId, UUID addressId, boolean primary) {
    return CompanyAddress.builder()
        .companyId(companyId)
        .addressId(addressId)
        .isPrimary(primary)
        .isHeadquarters(false)
        .build();
  }

  @Nested
  @DisplayName("assign (base)")
  class Assign {

    @Test
    void throwsWhenCompanyNotFound() {
      when(companyRepository.findByTenantIdAndId(TENANT_ID, COMPANY_ID))
          .thenReturn(Optional.empty());

      assertThatThrownBy(() -> service.assign(COMPANY_ID, ADDRESS_ID, true))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Company not found");
    }

    @Test
    void throwsWhenAddressNotFound() {
      when(companyRepository.findByTenantIdAndId(TENANT_ID, COMPANY_ID))
          .thenReturn(Optional.of(company(COMPANY_ID)));
      when(addressRepository.findById(ADDRESS_ID)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> service.assign(COMPANY_ID, ADDRESS_ID, true))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Address not found");
    }

    @Test
    void throwsWhenAddressDifferentTenant() {
      when(companyRepository.findByTenantIdAndId(TENANT_ID, COMPANY_ID))
          .thenReturn(Optional.of(company(COMPANY_ID)));
      Address addr = address(ADDRESS_ID);
      addr.setTenantId(UUID.randomUUID());
      when(addressRepository.findById(ADDRESS_ID)).thenReturn(Optional.of(addr));

      assertThatThrownBy(() -> service.assign(COMPANY_ID, ADDRESS_ID, true))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("does not belong");
    }

    @Test
    void throwsWhenAssignmentAlreadyExists() {
      when(companyRepository.findByTenantIdAndId(TENANT_ID, COMPANY_ID))
          .thenReturn(Optional.of(company(COMPANY_ID)));
      when(addressRepository.findById(ADDRESS_ID)).thenReturn(Optional.of(address(ADDRESS_ID)));
      when(companyAddressRepository.findByCompanyIdAndAddressId(COMPANY_ID, ADDRESS_ID))
          .thenReturn(Optional.of(junction(COMPANY_ID, ADDRESS_ID, false)));

      assertThatThrownBy(() -> service.assign(COMPANY_ID, ADDRESS_ID, true))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("already exists");
    }

    @Test
    void clearsExistingPrimaryWhenAssigningNewPrimary() {
      when(companyRepository.findByTenantIdAndId(TENANT_ID, COMPANY_ID))
          .thenReturn(Optional.of(company(COMPANY_ID)));
      when(addressRepository.findById(ADDRESS_ID)).thenReturn(Optional.of(address(ADDRESS_ID)));
      when(companyAddressRepository.findByCompanyIdAndAddressId(COMPANY_ID, ADDRESS_ID))
          .thenReturn(Optional.empty());
      CompanyAddress existingPrimary = junction(COMPANY_ID, ADDRESS_ID_2, true);
      when(companyAddressRepository.findPrimaryByCompanyId(COMPANY_ID))
          .thenReturn(Optional.of(existingPrimary));
      when(companyAddressRepository.save(any(CompanyAddress.class)))
          .thenAnswer(inv -> inv.getArgument(0));

      service.assign(COMPANY_ID, ADDRESS_ID, true);

      ArgumentCaptor<CompanyAddress> captor = ArgumentCaptor.forClass(CompanyAddress.class);
      verify(companyAddressRepository, times(2)).save(captor.capture());
      List<CompanyAddress> saved = captor.getAllValues();
      assertThat(saved).hasSize(2); // existing primary unset + new junction
      assertThat(existingPrimary.getIsPrimary()).isFalse();
      assertThat(saved.get(1).getCompanyId()).isEqualTo(COMPANY_ID);
      assertThat(saved.get(1).getAddressId()).isEqualTo(ADDRESS_ID);
      assertThat(saved.get(1).getIsPrimary()).isTrue();
    }

    @Test
    void savesJunctionAndPublishesEvent() {
      when(companyRepository.findByTenantIdAndId(TENANT_ID, COMPANY_ID))
          .thenReturn(Optional.of(company(COMPANY_ID)));
      when(addressRepository.findById(ADDRESS_ID)).thenReturn(Optional.of(address(ADDRESS_ID)));
      when(companyAddressRepository.findByCompanyIdAndAddressId(COMPANY_ID, ADDRESS_ID))
          .thenReturn(Optional.empty());
      when(companyAddressRepository.save(any(CompanyAddress.class)))
          .thenAnswer(inv -> inv.getArgument(0));

      CompanyAddress result = service.assign(COMPANY_ID, ADDRESS_ID, false);

      verify(companyAddressRepository).save(any(CompanyAddress.class));
      verify(eventPublisher).publish(any());
      assertThat(result.getCompanyId()).isEqualTo(COMPANY_ID);
      assertThat(result.getAddressId()).isEqualTo(ADDRESS_ID);
      assertThat(result.getIsPrimary()).isFalse();
    }
  }

  @Nested
  @DisplayName("unassign")
  class Unassign {

    @Test
    void throwsWhenAssignmentNotFound() {
      when(companyAddressRepository.findByCompanyIdAndAddressId(COMPANY_ID, ADDRESS_ID))
          .thenReturn(Optional.empty());

      assertThatThrownBy(() -> service.unassign(COMPANY_ID, ADDRESS_ID))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Assignment not found");
    }

    @Test
    void deletesExistingAssignment() {
      CompanyAddress existing = junction(COMPANY_ID, ADDRESS_ID, true);
      when(companyAddressRepository.findByCompanyIdAndAddressId(COMPANY_ID, ADDRESS_ID))
          .thenReturn(Optional.of(existing));

      service.unassign(COMPANY_ID, ADDRESS_ID);

      verify(companyAddressRepository).delete(existing);
    }
  }

  @Nested
  @DisplayName("setPrimary")
  class SetPrimary {

    @Test
    void throwsWhenAssignmentNotFound() {
      when(companyAddressRepository.findByCompanyIdAndAddressId(COMPANY_ID, ADDRESS_ID))
          .thenReturn(Optional.empty());

      assertThatThrownBy(() -> service.setPrimary(COMPANY_ID, ADDRESS_ID))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Assignment not found");
    }

    @Test
    void clearsPreviousPrimaryAndSetsNewPrimary() {
      CompanyAddress target = junction(COMPANY_ID, ADDRESS_ID, false);
      CompanyAddress previousPrimary = junction(COMPANY_ID, ADDRESS_ID_2, true);
      when(companyAddressRepository.findByCompanyIdAndAddressId(COMPANY_ID, ADDRESS_ID))
          .thenReturn(Optional.of(target));
      when(companyAddressRepository.findPrimaryByCompanyId(COMPANY_ID))
          .thenReturn(Optional.of(previousPrimary));
      when(companyAddressRepository.save(any(CompanyAddress.class)))
          .thenAnswer(inv -> inv.getArgument(0));

      CompanyAddress result = service.setPrimary(COMPANY_ID, ADDRESS_ID);

      verify(companyAddressRepository).save(previousPrimary);
      assertThat(previousPrimary.getIsPrimary()).isFalse();
      verify(companyAddressRepository).save(target);
      assertThat(target.getIsPrimary()).isTrue();
      assertThat(result).isSameAs(target);
    }
  }

  @Nested
  @DisplayName("getPrimary / getByParent")
  class GetPrimaryAndByParent {

    @Test
    void getPrimaryReturnsPrimaryFromRepository() {
      CompanyAddress primary = junction(COMPANY_ID, ADDRESS_ID, true);
      when(companyAddressRepository.findPrimaryByCompanyId(COMPANY_ID))
          .thenReturn(Optional.of(primary));

      Optional<CompanyAddress> result = service.getPrimary(COMPANY_ID);

      assertThat(result).contains(primary);
    }

    @Test
    void getByParentReturnsListFromRepository() {
      List<CompanyAddress> list =
          List.of(
              junction(COMPANY_ID, ADDRESS_ID, true), junction(COMPANY_ID, ADDRESS_ID_2, false));
      when(companyAddressRepository.findByTenantIdAndCompanyId(TENANT_ID, COMPANY_ID))
          .thenReturn(list);

      List<CompanyAddress> result = service.getByParent(COMPANY_ID);

      assertThat(result).isEqualTo(list);
    }
  }
}
