package com.fabricmanagement.common.platform.company.app;

import com.fabricmanagement.common.infrastructure.assignment.BaseAssignmentService;
import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.platform.communication.domain.Address;
import com.fabricmanagement.common.platform.communication.infra.repository.AddressRepository;
import com.fabricmanagement.common.platform.company.domain.CompanyAddress;
import com.fabricmanagement.common.platform.company.domain.event.AddressAssignedEvent;
import com.fabricmanagement.common.platform.company.infra.repository.CompanyAddressRepository;
import com.fabricmanagement.common.platform.company.infra.repository.CompanyRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Company–Address assignment service. Extends {@link BaseAssignmentService}; adds isHeadquarters
 * and assign overload.
 */
@Service
@Slf4j
public class CompanyAddressAssignmentService extends BaseAssignmentService<CompanyAddress> {

  private final CompanyRepository companyRepository;
  private final AddressRepository addressRepository;
  private final CompanyAddressRepository companyAddressRepository;
  private final DomainEventPublisher eventPublisher;

  public CompanyAddressAssignmentService(
      CompanyRepository companyRepository,
      AddressRepository addressRepository,
      CompanyAddressRepository companyAddressRepository,
      DomainEventPublisher eventPublisher) {
    this.companyRepository = companyRepository;
    this.addressRepository = addressRepository;
    this.companyAddressRepository = companyAddressRepository;
    this.eventPublisher = eventPublisher;
  }

  @Override
  protected void onAfterAssign(CompanyAddress junction) {
    eventPublisher.publish(
        new AddressAssignedEvent(
            TenantContext.getCurrentTenantId(), junction.getCompanyId(), junction.getAddressId()));
  }

  @Override
  protected JpaRepository<CompanyAddress, ?> getRepository() {
    return companyAddressRepository;
  }

  @Override
  protected void validateParentExists(UUID parentId) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    companyRepository
        .findByTenantIdAndId(tenantId, parentId)
        .orElseThrow(() -> new IllegalArgumentException("Company not found"));
  }

  @Override
  protected void validateChildExists(UUID childId) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    Address address =
        addressRepository
            .findById(childId)
            .orElseThrow(() -> new IllegalArgumentException("Address not found"));
    if (!address.getTenantId().equals(tenantId)) {
      throw new IllegalArgumentException("Address does not belong to current tenant");
    }
  }

  @Override
  protected Optional<CompanyAddress> findExisting(UUID parentId, UUID childId) {
    return companyAddressRepository.findByCompanyIdAndAddressId(parentId, childId);
  }

  @Override
  protected Optional<CompanyAddress> findPrimaryByParent(UUID parentId) {
    return companyAddressRepository.findPrimaryByCompanyId(parentId);
  }

  @Override
  protected List<CompanyAddress> findByParent(UUID parentId) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    return companyAddressRepository.findByTenantIdAndCompanyId(tenantId, parentId);
  }

  @Override
  protected CompanyAddress buildJunction(UUID parentId, UUID childId, Boolean primaryFlag) {
    return CompanyAddress.builder()
        .companyId(parentId)
        .addressId(childId)
        .isPrimary(Boolean.TRUE.equals(primaryFlag))
        .isHeadquarters(false)
        .build();
  }

  /** Assign address to company with primary and headquarters flags. */
  @Transactional
  public CompanyAddress assignAddress(
      UUID companyId, UUID addressId, Boolean isPrimary, Boolean isHeadquarters) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    log.info(
        "Assigning address to company: companyId={}, addressId={}, isPrimary={}, isHQ={}",
        companyId,
        addressId,
        isPrimary,
        isHeadquarters);

    validateParentExists(companyId);
    validateChildExists(addressId);
    validateAssignment(companyId, addressId);

    if (companyAddressRepository.findByCompanyIdAndAddressId(companyId, addressId).isPresent()) {
      throw new IllegalArgumentException("Address is already assigned to this company");
    }

    if (Boolean.TRUE.equals(isPrimary)) {
      companyAddressRepository
          .findPrimaryByCompanyId(companyId)
          .ifPresent(
              existing -> {
                existing.setIsPrimary(false);
                companyAddressRepository.save(existing);
              });
    }
    if (Boolean.TRUE.equals(isHeadquarters)) {
      companyAddressRepository
          .findHeadquartersByCompanyId(companyId)
          .ifPresent(
              existing -> {
                existing.setIsHeadquarters(false);
                companyAddressRepository.save(existing);
              });
    }

    CompanyAddress junction =
        CompanyAddress.builder()
            .companyId(companyId)
            .addressId(addressId)
            .isPrimary(isPrimary != null ? isPrimary : false)
            .isHeadquarters(isHeadquarters != null ? isHeadquarters : false)
            .build();
    CompanyAddress saved = companyAddressRepository.save(junction);
    onAfterAssign(saved);
    return saved;
  }

  @Transactional
  public void removeAddress(UUID companyId, UUID addressId) {
    unassign(companyId, addressId);
  }

  @Transactional
  public CompanyAddress setAsPrimary(UUID companyId, UUID addressId) {
    return setPrimary(companyId, addressId);
  }

  @Transactional
  public CompanyAddress setAsHeadquarters(UUID companyId, UUID addressId) {
    log.info("Setting headquarters: companyId={}, addressId={}", companyId, addressId);
    CompanyAddress junction =
        companyAddressRepository
            .findByCompanyIdAndAddressId(companyId, addressId)
            .orElseThrow(() -> new IllegalArgumentException("Address assignment not found"));
    companyAddressRepository
        .findHeadquartersByCompanyId(companyId)
        .ifPresent(
            existing -> {
              if (!existing.getAddressId().equals(addressId)) {
                existing.setIsHeadquarters(false);
                companyAddressRepository.save(existing);
              }
            });
    junction.setIsHeadquarters(true);
    return companyAddressRepository.save(junction);
  }

  @Transactional(readOnly = true)
  public List<CompanyAddress> getCompanyAddresses(UUID companyId) {
    return getByParent(companyId);
  }

  @Transactional(readOnly = true)
  public Optional<CompanyAddress> getPrimaryAddress(UUID companyId) {
    return getPrimary(companyId);
  }

  @Transactional(readOnly = true)
  public Optional<CompanyAddress> getHeadquarters(UUID companyId) {
    return companyAddressRepository.findHeadquartersByCompanyId(companyId);
  }
}
