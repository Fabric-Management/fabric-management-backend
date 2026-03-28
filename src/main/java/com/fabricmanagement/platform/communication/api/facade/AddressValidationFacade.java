package com.fabricmanagement.platform.communication.api.facade;

import com.fabricmanagement.platform.communication.app.AddressValidationService;
import com.fabricmanagement.platform.communication.domain.Address;
import com.fabricmanagement.platform.communication.dto.AddressDto;
import com.fabricmanagement.platform.communication.dto.ValidateAddressRequest;
import com.fabricmanagement.platform.communication.mapper.AddressMapper;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AddressValidationFacade {

  private final AddressValidationService addressValidationService;
  private final AddressMapper mapper;

  @Transactional
  public AddressDto validateAndCreateAddress(ValidateAddressRequest request) {
    Address address = addressValidationService.validateAndCreateAddress(request);
    return mapper.toDto(address);
  }

  @Transactional
  public AddressDto revalidateAddress(UUID addressId) {
    Address address = addressValidationService.revalidateAddress(addressId);
    return mapper.toDto(address);
  }
}
