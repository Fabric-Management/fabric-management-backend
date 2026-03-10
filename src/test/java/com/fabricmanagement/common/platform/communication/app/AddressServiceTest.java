package com.fabricmanagement.common.platform.communication.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.platform.communication.domain.Address;
import com.fabricmanagement.common.platform.communication.domain.AddressType;
import com.fabricmanagement.common.platform.communication.infra.repository.AddressRepository;
import com.fabricmanagement.common.platform.organization.infra.repository.OrganizationAddressRepository;
import com.fabricmanagement.common.platform.user.infra.repository.UserWorkLocationRepository;
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
@DisplayName("AddressService")
class AddressServiceTest {

  private static final UUID TENANT_ID = UUID.randomUUID();

  @Mock private AddressRepository addressRepository;
  @Mock private OrganizationAddressRepository organizationAddressRepository;
  @Mock private UserWorkLocationRepository userWorkLocationRepository;

  @InjectMocks private AddressService service;

  @BeforeEach
  void setUp() {
    TenantContext.setCurrentTenantId(TENANT_ID);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Nested
  @DisplayName("createAddress")
  class CreateAddress {

    @Test
    void createsAndSavesAddress() {
      when(addressRepository.save(any(Address.class))).thenAnswer(inv -> inv.getArgument(0));

      Address result =
          service.createAddress(
              "123 Main St",
              "Istanbul",
              "Istanbul",
              "34000",
              "Turkey",
              AddressType.HEADQUARTERS,
              "HQ");

      ArgumentCaptor<Address> captor = ArgumentCaptor.forClass(Address.class);
      verify(addressRepository).save(captor.capture());
      Address saved = captor.getValue();
      assertThat(saved.getStreetAddress()).isEqualTo("123 Main St");
      assertThat(saved.getCity()).isEqualTo("Istanbul");
      assertThat(saved.getState()).isEqualTo("Istanbul");
      assertThat(saved.getPostalCode()).isEqualTo("34000");
      assertThat(saved.getCountry()).isEqualTo("Turkey");
      assertThat(saved.getAddressType()).isEqualTo(AddressType.HEADQUARTERS);
      assertThat(saved.getLabel()).isEqualTo("HQ");
      assertThat(result).isSameAs(saved);
    }
  }

  @Nested
  @DisplayName("findById")
  class FindById {

    @Test
    void returnsEmptyWhenAddressBelongsToOtherTenant() {
      UUID addressId = UUID.randomUUID();
      Address other = Address.builder().build();
      other.setId(addressId);
      other.setTenantId(UUID.randomUUID());
      when(addressRepository.findById(addressId)).thenReturn(Optional.of(other));

      Optional<Address> result = service.findById(addressId);

      assertThat(result).isEmpty();
    }

    @Test
    void returnsAddressWhenSameTenant() {
      UUID addressId = UUID.randomUUID();
      Address address = Address.builder().build();
      address.setId(addressId);
      address.setTenantId(TENANT_ID);
      when(addressRepository.findById(addressId)).thenReturn(Optional.of(address));

      Optional<Address> result = service.findById(addressId);

      assertThat(result).contains(address);
    }
  }

  @Nested
  @DisplayName("updateAddress")
  class UpdateAddress {

    @Test
    void updatesFieldsAndSaves() {
      UUID addressId = UUID.randomUUID();
      Address address = Address.builder().streetAddress("Old").city("OldCity").build();
      address.setId(addressId);
      address.setTenantId(TENANT_ID);
      when(addressRepository.findById(addressId)).thenReturn(Optional.of(address));
      when(addressRepository.save(any(Address.class))).thenAnswer(inv -> inv.getArgument(0));

      Address result =
          service.updateAddress(
              addressId,
              "New St",
              null,
              "NewCity",
              "State",
              null,
              "12345",
              "Country",
              null,
              AddressType.OFFICE,
              "New Label",
              null,
              null,
              null);

      verify(addressRepository).save(address);
      assertThat(address.getStreetAddress()).isEqualTo("New St");
      assertThat(address.getCity()).isEqualTo("NewCity");
      assertThat(address.getState()).isEqualTo("State");
      assertThat(address.getPostalCode()).isEqualTo("12345");
      assertThat(address.getCountry()).isEqualTo("Country");
      assertThat(address.getLabel()).isEqualTo("New Label");
      assertThat(result).isSameAs(address);
    }
  }

  @Nested
  @DisplayName("deleteAddress")
  class DeleteAddress {

    @Test
    void softDeletesAddress() {
      UUID addressId = UUID.randomUUID();
      Address address = Address.builder().build();
      address.setId(addressId);
      address.setTenantId(TENANT_ID);
      when(addressRepository.findById(addressId)).thenReturn(Optional.of(address));
      when(addressRepository.save(any(Address.class))).thenAnswer(inv -> inv.getArgument(0));
      when(organizationAddressRepository.findByAddressIdIncludingDeleted(addressId))
          .thenReturn(Optional.empty());

      service.deleteAddress(addressId);

      verify(addressRepository).save(address);
      assertThat(address.getIsActive()).isFalse();
    }
  }
}
