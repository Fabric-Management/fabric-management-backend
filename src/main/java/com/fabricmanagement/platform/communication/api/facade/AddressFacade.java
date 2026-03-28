package com.fabricmanagement.platform.communication.api.facade;

import com.fabricmanagement.platform.communication.app.AddressService;
import com.fabricmanagement.platform.communication.domain.Address;
import com.fabricmanagement.platform.communication.domain.AddressType;
import com.fabricmanagement.platform.communication.dto.AddressDto;
import com.fabricmanagement.platform.communication.dto.CreateAddressRequest;
import com.fabricmanagement.platform.communication.dto.UpdateAddressRequest;
import com.fabricmanagement.platform.communication.mapper.AddressMapper;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AddressFacade {

  private final AddressService addressService;
  private final AddressMapper mapper;

  @Transactional
  public AddressDto createAddress(CreateAddressRequest request) {
    Address address = addressService.createAddress(request);
    return mapper.toDto(address);
  }

  @Transactional(readOnly = true)
  public AddressDto getAddress(UUID id) {
    Address address =
        addressService
            .findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Address not found"));
    return mapper.toDto(address);
  }

  @Transactional(readOnly = true)
  public List<AddressDto> getAddressesByType(AddressType type) {
    return addressService.findByType(type).stream().map(mapper::toDto).collect(Collectors.toList());
  }

  @Transactional
  public AddressDto updateAddress(UUID id, UpdateAddressRequest request) {
    Address address =
        addressService.updateAddress(
            id,
            request.getStreetAddress(),
            request.getAddressLine2(),
            request.getCity(),
            request.getState(),
            request.getDistrict(),
            request.getPostalCode(),
            request.getCountry(),
            request.getCountryCode(),
            request.getAddressType(),
            request.getLabel(),
            request.getContactPerson(),
            request.getContactPhone(),
            request.getContactEmail());
    return mapper.toDto(address);
  }

  @Transactional
  public void deleteAddress(UUID id) {
    addressService.deleteAddress(id);
  }
}
