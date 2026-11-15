package com.fabricmanagement.platform.user.api.facade;

import com.fabricmanagement.platform.communication.app.AddressService;
import com.fabricmanagement.platform.communication.domain.Address;
import com.fabricmanagement.platform.communication.dto.AddressDto;
import com.fabricmanagement.platform.communication.dto.CreateAddressRequest;
import com.fabricmanagement.platform.communication.dto.UpdateAddressRequest;
import com.fabricmanagement.platform.user.app.UserAddressAssignmentService;
import com.fabricmanagement.platform.user.dto.UserAddressDto;
import com.fabricmanagement.platform.user.mapper.UserAddressMapper;
import com.fabricmanagement.platform.user.security.UserContactDetailsAccess;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserAddressFacade {

  private final UserAddressAssignmentService userAddressAssignmentService;
  private final AddressService addressService;
  private final UserContactDetailsAccess access;
  private final UserAddressMapper userAddressMapper;

  @Transactional(readOnly = true)
  public List<UserAddressDto> getUserAddresses(UUID userId) {
    access.requireRead(userId);
    return userAddressMapper.toDtoList(userAddressAssignmentService.getUserAddresses(userId));
  }

  @Transactional(readOnly = true)
  public Optional<UserAddressDto> getPrimaryAddress(UUID userId) {
    access.requireRead(userId);
    return userAddressAssignmentService.getPrimaryAddress(userId).map(userAddressMapper::toDto);
  }

  @Transactional(readOnly = true)
  public List<UserAddressDto> getWorkAddresses(UUID userId) {
    access.requireRead(userId);
    return userAddressMapper.toDtoList(userAddressAssignmentService.getWorkAddresses(userId));
  }

  @Transactional
  public UserAddressDto assignAddress(
      UUID userId, UUID addressId, Boolean isPrimary, Boolean isWorkAddress) {
    return userAddressMapper.toDto(
        userAddressAssignmentService.assignAddress(userId, addressId, isPrimary, isWorkAddress));
  }

  @Transactional
  public UserAddressDto createAndAssignAddress(
      UUID userId, CreateAddressRequest request, Boolean isPrimary, Boolean isWorkAddress) {
    access.requireWrite(userId);
    Address address = addressService.createAddress(request);
    return assignAddress(userId, address.getId(), isPrimary, isWorkAddress);
  }

  @Transactional
  public UserAddressDto setAsPrimary(UUID userId, UUID addressId) {
    return userAddressMapper.toDto(userAddressAssignmentService.setAsPrimary(userId, addressId));
  }

  @Transactional
  public void removeAddress(UUID userId, UUID addressId) {
    userAddressAssignmentService.removeAddress(userId, addressId);
  }

  @Transactional
  public AddressDto updateAddress(UUID userId, UUID addressId, UpdateAddressRequest request) {
    return AddressDto.from(userAddressAssignmentService.updateAddress(userId, addressId, request));
  }
}
