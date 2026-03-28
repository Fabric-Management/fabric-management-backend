package com.fabricmanagement.platform.organization.api.facade;

import com.fabricmanagement.platform.communication.app.AddressService;
import com.fabricmanagement.platform.communication.domain.Address;
import com.fabricmanagement.platform.communication.dto.AssignAddressRequest;
import com.fabricmanagement.platform.communication.dto.CreateAddressRequest;
import com.fabricmanagement.platform.organization.app.OrganizationAddressAssignmentService;
import com.fabricmanagement.platform.organization.domain.OrganizationAddress;
import com.fabricmanagement.platform.organization.dto.AddressDeletionImpactDto;
import com.fabricmanagement.platform.organization.dto.OrganizationAddressDto;
import com.fabricmanagement.platform.organization.mapper.OrganizationAddressMapper;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrganizationAddressFacade {

  private final OrganizationAddressAssignmentService assignmentService;
  private final AddressService addressService;
  private final OrganizationAddressMapper mapper;

  @Transactional(readOnly = true)
  public List<OrganizationAddressDto> getOrganizationAddresses(UUID organizationId) {
    return assignmentService.getOrganizationAddresses(organizationId).stream()
        .map(mapper::toDto)
        .collect(Collectors.toList());
  }

  @Transactional(readOnly = true)
  public OrganizationAddressDto getPrimaryAddress(UUID organizationId) {
    OrganizationAddress address =
        assignmentService
            .getPrimaryAddress(organizationId)
            .orElseThrow(() -> new IllegalArgumentException("No primary address found"));
    return mapper.toDto(address);
  }

  @Transactional(readOnly = true)
  public OrganizationAddressDto getHeadquarters(UUID organizationId) {
    OrganizationAddress address =
        assignmentService
            .getHeadquarters(organizationId)
            .orElseThrow(() -> new IllegalArgumentException("No headquarters found"));
    return mapper.toDto(address);
  }

  @Transactional
  public OrganizationAddressDto assignAddress(UUID organizationId, AssignAddressRequest request) {
    OrganizationAddress address =
        assignmentService.assignAddress(
            organizationId,
            request.getAddressId(),
            request.getIsPrimary(),
            request.getIsHeadquarters());
    return mapper.toDto(address);
  }

  @Transactional
  public OrganizationAddressDto createAndAssignAddress(
      UUID organizationId,
      CreateAddressRequest request,
      Boolean isPrimary,
      Boolean isHeadquarters) {
    Address address = addressService.createAddress(request);
    OrganizationAddress orgAddress =
        assignmentService.assignAddress(organizationId, address.getId(), isPrimary, isHeadquarters);
    return mapper.toDto(orgAddress);
  }

  @Transactional
  public OrganizationAddressDto setAsPrimary(UUID organizationId, UUID addressId) {
    return mapper.toDto(assignmentService.setAsPrimary(organizationId, addressId));
  }

  @Transactional
  public OrganizationAddressDto setAsHeadquarters(UUID organizationId, UUID addressId) {
    return mapper.toDto(assignmentService.setAsHeadquarters(organizationId, addressId));
  }

  @Transactional(readOnly = true)
  public AddressDeletionImpactDto getAddressDeletionImpact(UUID organizationId, UUID addressId) {
    return assignmentService.getAddressDeletionImpact(organizationId, addressId);
  }

  @Transactional
  public void safeRemoveAddress(UUID organizationId, UUID addressId) {
    assignmentService.safeRemoveAddress(organizationId, addressId);
  }
}
