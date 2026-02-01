package com.fabricmanagement.common.platform.company.app;

import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.exception.TaxIdAlreadyExistsException;
import com.fabricmanagement.common.platform.communication.app.AddressService;
import com.fabricmanagement.common.platform.communication.app.ContactService;
import com.fabricmanagement.common.platform.communication.domain.AddressType;
import com.fabricmanagement.common.platform.communication.domain.ContactType;
import com.fabricmanagement.common.platform.company.domain.Company;
import com.fabricmanagement.common.platform.company.domain.event.CompanyCreatedEvent;
import com.fabricmanagement.common.platform.company.dto.AddressRequest;
import com.fabricmanagement.common.platform.company.dto.CompanyDto;
import com.fabricmanagement.common.platform.company.dto.ContactRequest;
import com.fabricmanagement.common.platform.company.dto.CreateCompanyRequest;
import com.fabricmanagement.common.platform.company.dto.CreateCompanyWithContactRequest;
import com.fabricmanagement.common.platform.company.dto.CreateTenantCompanyRequest;
import com.fabricmanagement.common.platform.company.infra.repository.CompanyRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Company creation: basic create and create-with-contact. Validates tax ID uniqueness and hierarchy
 * (parent validation, circular reference).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CompanyCreationService {

  private final CompanyRepository companyRepository;
  private final CompanyHierarchyService hierarchyService;
  private final ContactService contactService;
  private final AddressService addressService;
  private final CompanyContactAssignmentService companyContactAssignmentService;
  private final CompanyAddressAssignmentService companyAddressAssignmentService;
  private final DomainEventPublisher eventPublisher;

  @Transactional
  public CompanyDto createCompany(CreateCompanyRequest request) {
    UUID tenantId = TenantContext.getCurrentTenantIdOrNull();
    if (tenantId == null) {
      throw new IllegalStateException("Tenant context must be set to create a company");
    }

    if (companyRepository.existsByTenantIdAndTaxId(tenantId, request.getTaxId())) {
      throw new TaxIdAlreadyExistsException(
          "Company with this tax ID already exists in your organization");
    }

    if (request.getParentCompanyId() != null) {
      hierarchyService.validateParent(request.getParentCompanyId());
    }

    if (request.getCompanyType().isTenant()) {
      log.warn(
          "Creating tenant-type company via normal endpoint. "
              + "Consider using onboarding endpoints for proper tenant setup.");
    }

    Company company =
        Company.create(request.getCompanyName(), request.getTaxId(), request.getCompanyType());
    company.setParent(request.getParentCompanyId());
    Company saved = companyRepository.save(company);

    eventPublisher.publish(
        new CompanyCreatedEvent(
            saved.getTenantId(),
            saved.getId(),
            saved.getCompanyName(),
            saved.getCompanyType().name()));

    log.info(
        "Company created: id={}, uid={}, tenantId={}",
        saved.getId(),
        saved.getUid(),
        saved.getTenantId());
    return CompanyDto.from(saved);
  }

  /**
   * Create root tenant company (tenant_id = company_id). Used only during tenant onboarding.
   *
   * <p><b>CRITICAL:</b> This is the ONLY place where tenant_id equals company_id. Validates company
   * type is tenant and tax ID is globally unique.
   */
  @Transactional
  public CompanyDto createTenantCompany(CreateTenantCompanyRequest request) {
    log.debug(
        "Creating tenant company: name={}, type={}",
        request.getCompanyName(),
        request.getCompanyType());

    if (!request.getCompanyType().isTenant()) {
      throw new IllegalArgumentException("Company type must be a tenant type");
    }
    if (companyRepository.existsByTaxId(request.getTaxId())) {
      throw new TaxIdAlreadyExistsException("Company with this tax ID already exists");
    }

    Company company =
        Company.create(request.getCompanyName(), request.getTaxId(), request.getCompanyType());

    String tenantUid = generateTenantUidFromCompanyName(request.getCompanyName());
    company.setUid(tenantUid);
    TenantContext.setCurrentTenantUid(tenantUid);
    log.debug(
        "Tenant UID generated and set: {} for company: {}", tenantUid, request.getCompanyName());

    Company saved = companyRepository.save(company);
    saved.setTenantId(saved.getId());
    Company finalCompany = companyRepository.save(saved);
    TenantContext.setCurrentTenantUid(finalCompany.getUid());

    eventPublisher.publish(
        new CompanyCreatedEvent(
            finalCompany.getTenantId(),
            finalCompany.getId(),
            finalCompany.getCompanyName(),
            finalCompany.getCompanyType().name()));

    log.info(
        "Tenant company created - id: {}, tenant_id: {}, uid: {}",
        finalCompany.getId(),
        finalCompany.getTenantId(),
        finalCompany.getUid());
    return CompanyDto.from(finalCompany);
  }

  /** Generate tenant UID from company name. Format: {PREFIX}-{SEQUENCE}, e.g. AKKAYALAR-001. */
  private String generateTenantUidFromCompanyName(String companyName) {
    if (companyName == null || companyName.isBlank()) {
      return "SYS-000";
    }
    String[] words = companyName.trim().split("\\s+");
    String base = words[0].toUpperCase().replaceAll("[^A-Z0-9]", "");
    String firstWord = base.isEmpty() ? "COMPANY" : base.substring(0, Math.min(10, base.length()));
    int counter = 1;
    String candidateUid;
    do {
      String sequence = String.format("%03d", counter);
      candidateUid = String.format("%s-%s", firstWord, sequence);
      if (companyRepository.findByUid(candidateUid).isEmpty()) {
        break;
      }
      counter++;
      if (counter > 999) {
        String uuidSuffix =
            UUID.randomUUID().toString().replace("-", "").substring(0, 4).toUpperCase();
        candidateUid = String.format("%s-%s", firstWord, uuidSuffix);
        log.warn(
            "Too many collisions for prefix {}, using UUID suffix: {}", firstWord, candidateUid);
        break;
      }
    } while (counter <= 999);
    log.debug("Generated tenant UID: {} from company name: {}", candidateUid, companyName);
    return candidateUid;
  }

  @Transactional
  public CompanyDto createCompanyWithContact(CreateCompanyWithContactRequest request) {
    CreateCompanyRequest basic =
        CreateCompanyRequest.builder()
            .companyName(request.getCompanyName())
            .taxId(request.getTaxId())
            .companyType(request.getCompanyType())
            .parentCompanyId(request.getParentCompanyId())
            .build();
    CompanyDto company = createCompany(basic);
    UUID tenantId = TenantContext.getCurrentTenantId();
    addCompanyContactAndAddress(company.getId(), tenantId, request);
    log.info("Company with contact/address created: companyId={}", company.getId());
    return company;
  }

  private void addCompanyContactAndAddress(
      UUID companyId, UUID tenantId, CreateCompanyWithContactRequest request) {
    boolean useNestedContacts = request.getContacts() != null && !request.getContacts().isEmpty();
    boolean useNestedAddresses =
        request.getAddresses() != null && !request.getAddresses().isEmpty();
    boolean hasFlatAddress =
        (request.getAddress() != null && !request.getAddress().isBlank())
            || (request.getCity() != null && !request.getCity().isBlank())
            || (request.getCountry() != null && !request.getCountry().isBlank());
    boolean hasFlatContact =
        (request.getEmail() != null && !request.getEmail().isBlank())
            || (request.getPhoneNumber() != null && !request.getPhoneNumber().isBlank());

    if (!useNestedContacts && !useNestedAddresses && !hasFlatAddress && !hasFlatContact) return;

    UUID originalTenantId = TenantContext.getCurrentTenantId();
    try {
      TenantContext.setCurrentTenantId(tenantId);

      if (useNestedAddresses) {
        for (AddressRequest ar : request.getAddresses()) {
          String street = ar.getStreetAddress() != null ? ar.getStreetAddress() : "";
          String city = ar.getCity() != null ? ar.getCity() : "";
          String country = ar.getCountry() != null ? ar.getCountry() : "";
          var address =
              addressService.createAddress(
                  street,
                  city,
                  ar.getState(),
                  ar.getPostalCode(),
                  country,
                  ar.getAddressType(),
                  ar.getAddressType().name());
          boolean primary = Boolean.TRUE.equals(ar.getIsPrimary());
          boolean hq = ar.getAddressType() == AddressType.HEADQUARTERS;
          companyAddressAssignmentService.assignAddress(companyId, address.getId(), primary, hq);
        }
      } else if (hasFlatAddress) {
        var address =
            addressService.createAddress(
                request.getAddress() != null ? request.getAddress() : "",
                request.getCity() != null ? request.getCity() : "",
                request.getState(),
                request.getPostalCode(),
                request.getCountry() != null ? request.getCountry() : "",
                AddressType.HEADQUARTERS,
                "Headquarters");
        companyAddressAssignmentService.assignAddress(companyId, address.getId(), true, true);
      }

      if (useNestedContacts) {
        for (ContactRequest cr : request.getContacts()) {
          var contact =
              contactService.createContact(
                  cr.getContactValue(),
                  cr.getContactType(),
                  cr.getContactType().name(),
                  false,
                  null);
          boolean isDefault = Boolean.TRUE.equals(cr.getIsDefault());
          companyContactAssignmentService.assignContact(
              companyId, contact.getId(), isDefault, cr.getDepartment());
        }
      } else {
        if (request.getPhoneNumber() != null && !request.getPhoneNumber().isBlank()) {
          var phoneContact =
              contactService.createContact(
                  request.getPhoneNumber(), ContactType.LANDLINE, "Main Phone", false, null);
          companyContactAssignmentService.assignContact(
              companyId, phoneContact.getId(), true, null);
        }
        if (request.getEmail() != null && !request.getEmail().isBlank()) {
          var emailContact =
              contactService.createContact(
                  request.getEmail(), ContactType.EMAIL, "Main Email", false, null);
          boolean isDefault =
              request.getPhoneNumber() == null || request.getPhoneNumber().isBlank();
          companyContactAssignmentService.assignContact(
              companyId, emailContact.getId(), isDefault, null);
        }
      }
    } finally {
      if (originalTenantId != null) {
        TenantContext.setCurrentTenantId(originalTenantId);
      }
    }
  }

  /**
   * Add company address and contact from flat fields. Used during tenant onboarding (Auth module)
   * via CompanyFacade. No-op if no address or contact data provided.
   */
  public void addCompanyAddressAndContactFlat(
      UUID companyId,
      UUID tenantId,
      String address,
      String city,
      String country,
      String phoneNumber,
      String email) {
    boolean hasAddressInfo = address != null || city != null || country != null;
    boolean hasContactInfo = phoneNumber != null || email != null;
    if (!hasAddressInfo && !hasContactInfo) {
      return;
    }
    UUID originalTenantId = TenantContext.getCurrentTenantId();
    try {
      TenantContext.setCurrentTenantId(tenantId);
      if (hasAddressInfo) {
        var addr =
            addressService.createAddress(
                address != null ? address : "",
                city != null ? city : "",
                null,
                null,
                country != null ? country : "",
                AddressType.HEADQUARTERS,
                "Headquarters");
        companyAddressAssignmentService.assignAddress(companyId, addr.getId(), true, true);
      }
      if (phoneNumber != null) {
        var phoneContact =
            contactService.createContact(
                phoneNumber, ContactType.LANDLINE, "Main Phone", false, null);
        companyContactAssignmentService.assignContact(companyId, phoneContact.getId(), true, null);
      }
      if (email != null) {
        var emailContact =
            contactService.createContact(email, ContactType.EMAIL, "Main Email", false, null);
        boolean isDefault = phoneNumber == null;
        companyContactAssignmentService.assignContact(
            companyId, emailContact.getId(), isDefault, null);
      }
    } catch (Exception e) {
      log.warn(
          "Failed to add company address/contact: companyId={}, error={}",
          companyId,
          e.getMessage());
    } finally {
      if (originalTenantId != null) {
        TenantContext.setCurrentTenantId(originalTenantId);
      }
    }
  }
}
