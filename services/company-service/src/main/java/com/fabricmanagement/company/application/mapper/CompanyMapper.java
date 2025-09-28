package com.fabricmanagement.company.application.mapper;

import com.fabricmanagement.company.application.dto.company.request.CreateCompanyRequest;
import com.fabricmanagement.company.application.dto.company.request.UpdateCompanyRequest;
import com.fabricmanagement.company.application.dto.company.response.CompanyDetailResponse;
import com.fabricmanagement.company.application.dto.company.response.CompanyResponse;
import com.fabricmanagement.company.domain.model.Company;
import org.mapstruct.*;

/**
 * MapStruct mapper for Company domain entity and DTOs.
 * Handles mapping between domain objects and application layer DTOs.
 */
@Mapper(
    componentModel = "spring",
    injectionStrategy = InjectionStrategy.CONSTRUCTOR,
    nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
    unmappedTargetPolicy = ReportingPolicy.WARN
)
public interface CompanyMapper {

    /**
     * Maps CreateCompanyRequest to Company domain entity.
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "primaryContactId", ignore = true)
    @Mapping(target = "employeeIds", ignore = true)
    @Mapping(target = "subsidiaryIds", ignore = true)
    @Mapping(target = "parentCompanyId", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    Company toDomain(CreateCompanyRequest request);

    /**
     * Maps Company domain entity to CompanyResponse DTO.
     */
    CompanyResponse toResponse(Company company);

    /**
     * Maps Company domain entity to CompanyDetailResponse DTO.
     */
    @Mapping(target = "contactInfo", ignore = true)
    @Mapping(target = "employees", ignore = true)
    CompanyDetailResponse toDetailResponse(Company company);

    /**
     * Updates Company domain entity from UpdateCompanyRequest.
     * Only updates non-null fields from the request.
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "primaryContactId", ignore = true)
    @Mapping(target = "employeeIds", ignore = true)
    @Mapping(target = "subsidiaryIds", ignore = true)
    @Mapping(target = "parentCompanyId", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    void updateDomainFromRequest(UpdateCompanyRequest request, @MappingTarget Company company);

    /**
     * Custom mapping for certifications - handles null and empty sets.
     */
    @AfterMapping
    default void mapCertifications(CreateCompanyRequest request, @MappingTarget Company company) {
        if (request.certifications() != null && !request.certifications().isEmpty()) {
            company.getCertifications().clear();
            company.getCertifications().addAll(request.certifications());
        }
    }

    /**
     * Custom mapping for business licenses - handles null and empty sets.
     */
    @AfterMapping
    default void mapBusinessLicenses(CreateCompanyRequest request, @MappingTarget Company company) {
        if (request.businessLicenses() != null && !request.businessLicenses().isEmpty()) {
            company.getBusinessLicenses().clear();
            company.getBusinessLicenses().addAll(request.businessLicenses());
        }
    }

    /**
     * Custom mapping for certifications during update - handles null and empty sets.
     */
    @AfterMapping
    default void updateCertifications(UpdateCompanyRequest request, @MappingTarget Company company) {
        if (request.certifications() != null) {
            company.getCertifications().clear();
            if (!request.certifications().isEmpty()) {
                company.getCertifications().addAll(request.certifications());
            }
        }
    }

    /**
     * Custom mapping for business licenses during update - handles null and empty sets.
     */
    @AfterMapping
    default void updateBusinessLicenses(UpdateCompanyRequest request, @MappingTarget Company company) {
        if (request.businessLicenses() != null) {
            company.getBusinessLicenses().clear();
            if (!request.businessLicenses().isEmpty()) {
                company.getBusinessLicenses().addAll(request.businessLicenses());
            }
        }
    }
}