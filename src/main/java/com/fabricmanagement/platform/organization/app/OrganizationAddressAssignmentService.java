package com.fabricmanagement.platform.organization.app;

import com.fabricmanagement.common.infrastructure.assignment.BaseAssignmentService;
import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.platform.common.exception.PlatformDomainException;
import com.fabricmanagement.platform.communication.domain.Address;
import com.fabricmanagement.platform.communication.infra.repository.AddressRepository;
import com.fabricmanagement.platform.organization.domain.OrganizationAddress;
import com.fabricmanagement.platform.organization.domain.event.OrganizationAddressAssignedEvent;
import com.fabricmanagement.platform.organization.dto.AddressDeletionImpactDto;
import com.fabricmanagement.platform.organization.infra.repository.OrganizationAddressRepository;
import com.fabricmanagement.platform.organization.infra.repository.OrganizationRepository;
import com.fabricmanagement.platform.user.domain.UserWorkLocation;
import com.fabricmanagement.platform.user.infra.repository.UserWorkLocationRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Organization–Address assignment service. Extends {@link BaseAssignmentService}; adds
 * isHeadquarters and assign overload.
 */
@Service
@Slf4j
public class OrganizationAddressAssignmentService
    extends BaseAssignmentService<OrganizationAddress> {

  private final OrganizationRepository organizationRepository;
  private final AddressRepository addressRepository;
  private final OrganizationAddressRepository organizationAddressRepository;
  private final UserWorkLocationRepository userWorkLocationRepository;
  private final DomainEventPublisher eventPublisher;

  public OrganizationAddressAssignmentService(
      OrganizationRepository organizationRepository,
      AddressRepository addressRepository,
      OrganizationAddressRepository organizationAddressRepository,
      UserWorkLocationRepository userWorkLocationRepository,
      DomainEventPublisher eventPublisher) {
    this.organizationRepository = organizationRepository;
    this.addressRepository = addressRepository;
    this.organizationAddressRepository = organizationAddressRepository;
    this.userWorkLocationRepository = userWorkLocationRepository;
    this.eventPublisher = eventPublisher;
  }

  @Override
  protected void onAfterAssign(OrganizationAddress junction) {
    eventPublisher.publish(
        new OrganizationAddressAssignedEvent(
            TenantContext.requireTenantId(),
            junction.getOrganizationId(),
            junction.getAddressId()));
  }

  @Override
  protected JpaRepository<OrganizationAddress, ?> getRepository() {
    return organizationAddressRepository;
  }

  @Override
  protected void validateParentExists(UUID parentId) {
    UUID tenantId = TenantContext.requireTenantId();
    organizationRepository
        .findByTenantIdAndId(tenantId, parentId)
        .orElseThrow(
            () -> new PlatformDomainException("Organization not found", "ORG_NOT_FOUND", 404));
  }

  @Override
  protected void validateChildExists(UUID childId) {
    UUID tenantId = TenantContext.requireTenantId();
    Address address =
        addressRepository
            .findById(childId)
            .orElseThrow(
                () ->
                    new PlatformDomainException("Address not found", "ORG_ADDRESS_NOT_FOUND", 404));
    if (!address.getTenantId().equals(tenantId)) {
      throw new PlatformDomainException(
          "Address does not belong to current tenant", "ORG_ADDRESS_TENANT_MISMATCH", 403);
    }
  }

  @Override
  protected Optional<OrganizationAddress> findExisting(UUID parentId, UUID childId) {
    return organizationAddressRepository.findByOrganizationIdAndAddressId(parentId, childId);
  }

  @Override
  protected Optional<OrganizationAddress> findPrimaryByParent(UUID parentId) {
    return organizationAddressRepository.findPrimaryByOrganizationId(parentId);
  }

  @Override
  protected List<OrganizationAddress> findByParent(UUID parentId) {
    UUID tenantId = TenantContext.requireTenantId();
    return organizationAddressRepository.findWithAddressByTenantIdAndOrganizationId(
        tenantId, parentId);
  }

  @Override
  protected OrganizationAddress buildJunction(UUID parentId, UUID childId, Boolean primaryFlag) {
    return OrganizationAddress.builder()
        .organizationId(parentId)
        .addressId(childId)
        .isPrimary(Boolean.TRUE.equals(primaryFlag))
        .isHeadquarters(false)
        .build();
  }

  /** Assign address to organization with primary and headquarters flags. */
  @Transactional
  public OrganizationAddress assignAddress(
      UUID organizationId, UUID addressId, Boolean isPrimary, Boolean isHeadquarters) {
    log.info(
        "Assigning address to organization: organizationId={}, addressId={}, isPrimary={}, isHQ={}",
        organizationId,
        addressId,
        isPrimary,
        isHeadquarters);

    validateParentExists(organizationId);
    validateChildExists(addressId);
    validateAssignment(organizationId, addressId);

    if (organizationAddressRepository
        .findActiveByOrganizationIdAndAddressId(organizationId, addressId)
        .isPresent()) {
      throw new PlatformDomainException(
          "Address is already assigned to this organization", "ORG_ADDRESS_ALREADY_ASSIGNED", 409);
    }

    if (Boolean.TRUE.equals(isPrimary)) {
      organizationAddressRepository
          .findPrimaryByOrganizationId(organizationId)
          .ifPresent(
              existing -> {
                existing.setIsPrimary(false);
                organizationAddressRepository.save(existing);
              });
    }
    if (Boolean.TRUE.equals(isHeadquarters)) {
      organizationAddressRepository
          .findHeadquartersByOrganizationId(organizationId)
          .ifPresent(
              existing -> {
                existing.setIsHeadquarters(false);
                organizationAddressRepository.save(existing);
              });
    }

    OrganizationAddress junction =
        OrganizationAddress.builder()
            .organizationId(organizationId)
            .addressId(addressId)
            .isPrimary(isPrimary != null ? isPrimary : false)
            .isHeadquarters(isHeadquarters != null ? isHeadquarters : false)
            .build();
    OrganizationAddress saved = organizationAddressRepository.save(junction);
    onAfterAssign(saved);
    return saved;
  }

  @Transactional
  public void removeAddress(UUID organizationId, UUID addressId) {
    unassign(organizationId, addressId);
  }

  @Transactional
  public OrganizationAddress setAsPrimary(UUID organizationId, UUID addressId) {
    setPrimary(organizationId, addressId);
    return organizationAddressRepository
        .findActiveWithAddressByOrganizationIdAndAddressId(organizationId, addressId)
        .orElseThrow(
            () ->
                new PlatformDomainException(
                    "Address assignment not found", "ORG_ADDRESS_ASSIGNMENT_NOT_FOUND", 404));
  }

  @Transactional
  public OrganizationAddress setAsHeadquarters(UUID organizationId, UUID addressId) {
    log.info("Setting headquarters: organizationId={}, addressId={}", organizationId, addressId);
    OrganizationAddress junction =
        organizationAddressRepository
            .findActiveByOrganizationIdAndAddressId(organizationId, addressId)
            .orElseThrow(
                () ->
                    new PlatformDomainException(
                        "Address assignment not found", "ORG_ADDRESS_ASSIGNMENT_NOT_FOUND", 404));
    organizationAddressRepository
        .findHeadquartersByOrganizationId(organizationId)
        .ifPresent(
            existing -> {
              if (!existing.getAddressId().equals(addressId)) {
                existing.setIsHeadquarters(false);
                organizationAddressRepository.save(existing);
              }
            });
    junction.setIsHeadquarters(true);
    organizationAddressRepository.save(junction);
    return organizationAddressRepository
        .findActiveWithAddressByOrganizationIdAndAddressId(organizationId, addressId)
        .orElseThrow(
            () ->
                new PlatformDomainException(
                    "Address assignment not found", "ORG_ADDRESS_ASSIGNMENT_NOT_FOUND", 404));
  }

  @Transactional(readOnly = true)
  public List<OrganizationAddress> getOrganizationAddresses(UUID organizationId) {
    return getByParent(organizationId);
  }

  @Transactional(readOnly = true)
  public Optional<OrganizationAddress> getPrimaryAddress(UUID organizationId) {
    return getPrimary(organizationId);
  }

  @Transactional(readOnly = true)
  public Optional<OrganizationAddress> getHeadquarters(UUID organizationId) {
    return organizationAddressRepository.findHeadquartersByOrganizationId(organizationId);
  }

  /**
   * Analyzes the impact of deleting an organization address. Returns the number and details of
   * users whose work location would be affected.
   */
  @Transactional(readOnly = true)
  public AddressDeletionImpactDto getAddressDeletionImpact(UUID organizationId, UUID addressId) {
    UUID tenantId = TenantContext.requireTenantId();
    log.info(
        "Analyzing deletion impact: tenantId={}, organizationId={}, addressId={}",
        tenantId,
        organizationId,
        addressId);

    OrganizationAddress orgAddress =
        organizationAddressRepository
            .findActiveByOrganizationIdAndAddressId(organizationId, addressId)
            .orElseThrow(
                () ->
                    new PlatformDomainException(
                        "Organization address not found", "ORG_ADDRESS_NOT_FOUND", 404));

    Address address =
        addressRepository
            .findById(addressId)
            .orElseThrow(
                () ->
                    new PlatformDomainException("Address not found", "ORG_ADDRESS_NOT_FOUND", 404));

    List<UserWorkLocation> affectedLocations =
        userWorkLocationRepository.findByOrgAddressId(orgAddress.getAddressId());

    List<AddressDeletionImpactDto.AffectedUserDto> affectedUsers =
        affectedLocations.stream()
            .map(
                wl -> {
                  String displayName = wl.getUser() != null ? wl.getUser().getDisplayName() : null;
                  return AddressDeletionImpactDto.AffectedUserDto.builder()
                      .userId(wl.getUserId())
                      .displayName(displayName)
                      .isPrimaryLocation(Boolean.TRUE.equals(wl.getIsPrimary()))
                      .build();
                })
            .toList();

    return AddressDeletionImpactDto.builder()
        .addressId(addressId)
        .addressLabel(address.getLabel())
        .affectedUserCount(affectedUsers.size())
        .affectedUsers(affectedUsers)
        .hasOrganizationAssignment(true)
        .build();
  }

  /**
   * Safely removes an organization address with full cascade:
   *
   * <ol>
   *   <li>Hard-delete all UserWorkLocation junctions (users lose this location)
   *   <li>Soft-delete the OrganizationAddress junction
   *   <li>Soft-delete the underlying Address entity
   * </ol>
   */
  @Transactional
  public void safeRemoveAddress(UUID organizationId, UUID addressId) {
    UUID tenantId = TenantContext.requireTenantId();
    log.info(
        "Safe-removing org address: tenantId={}, organizationId={}, addressId={}",
        tenantId,
        organizationId,
        addressId);

    OrganizationAddress orgAddress =
        organizationAddressRepository
            .findByOrganizationIdAndAddressId(organizationId, addressId)
            .orElseThrow(
                () ->
                    new PlatformDomainException(
                        "Organization address not found", "ORG_ADDRESS_NOT_FOUND", 404));

    Address address =
        addressRepository
            .findById(addressId)
            .orElseThrow(
                () ->
                    new PlatformDomainException("Address not found", "ORG_ADDRESS_NOT_FOUND", 404));

    if (!address.getTenantId().equals(tenantId)) {
      throw new PlatformDomainException(
          "Address does not belong to current tenant", "ORG_ADDRESS_TENANT_MISMATCH", 403);
    }

    long affectedCount = userWorkLocationRepository.countByOrgAddressId(orgAddress.getAddressId());
    log.info(
        "Cascading deletion: {} user work-location assignments will be removed", affectedCount);

    userWorkLocationRepository.deleteAllByOrgAddressId(orgAddress.getAddressId());

    orgAddress.delete();
    organizationAddressRepository.save(orgAddress);

    address.delete();
    addressRepository.save(address);

    log.info(
        "Safe-remove completed: addressId={}, cascaded {} user locations",
        addressId,
        affectedCount);
  }
}
