package com.fabricmanagement.common.platform.company.api.controller;

import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.common.platform.company.app.CompanyService;
import com.fabricmanagement.common.platform.company.domain.CompanyCategory;
import com.fabricmanagement.common.platform.company.domain.CompanyType;
import com.fabricmanagement.common.platform.company.dto.CompanyDto;
import com.fabricmanagement.common.platform.company.dto.CreateCompanyRequest;
import com.fabricmanagement.common.platform.company.dto.CreateCompanyWithContactRequest;
import com.fabricmanagement.common.platform.company.dto.UpdateCompanyRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/common/companies")
@RequiredArgsConstructor
@Slf4j
public class CompanyController {

  private final CompanyService companyService;

  @PostMapping
  public ResponseEntity<ApiResponse<CompanyDto>> createCompany(
      @Valid @RequestBody CreateCompanyRequest request) {
    log.info("Creating company: {}", request.getCompanyName());

    CompanyDto created = companyService.createCompany(request);

    return ResponseEntity.ok(ApiResponse.success(created, "Company created successfully"));
  }

  /**
   * Orchestration endpoint: Create company with contact and address in single transaction.
   *
   * <p>This endpoint creates company, contact (email/phone), and address in one atomic operation.
   * If any step fails, entire transaction rolls back.
   *
   * <p>Use this endpoint when you want to create company with communication information in one
   * request.
   *
   * <p>Example request:
   *
   * <pre>
   * {
   *   "companyName": "ACME Corp",
   *   "taxId": "1234567890",
   *   "companyType": "VERTICAL_MILL",
   *   "email": "info@acme.com",
   *   "phoneNumber": "+905551234567",
   *   "address": "Ataturk Cad. No:1",
   *   "city": "Istanbul",
   *   "country": "Turkey"
   * }
   * </pre>
   */
  @PostMapping("/with-contact")
  public ResponseEntity<ApiResponse<CompanyDto>> createCompanyWithContact(
      @Valid @RequestBody CreateCompanyWithContactRequest request) {
    log.info("Creating company with contact/address: {}", request.getCompanyName());

    CompanyDto created = companyService.createCompanyWithContact(request);

    return ResponseEntity.ok(
        ApiResponse.success(created, "Company with contact and address created successfully"));
  }

  @GetMapping("/{id}")
  public ResponseEntity<ApiResponse<CompanyDto>> getCompany(@PathVariable UUID id) {
    log.debug("Getting company: id={}", id);

    CompanyDto company = companyService.getCompany(id);

    return ResponseEntity.ok(ApiResponse.success(company));
  }

  @GetMapping
  public ResponseEntity<ApiResponse<List<CompanyDto>>> getAllCompanies() {
    log.debug("Getting companies (excluding tenant's own company)");

    List<CompanyDto> companies = companyService.getOtherCompanies();

    return ResponseEntity.ok(ApiResponse.success(companies));
  }

  @GetMapping("/tenants")
  public ResponseEntity<ApiResponse<List<CompanyDto>>> getTenantCompanies() {
    log.debug("Getting tenant companies only");

    List<CompanyDto> tenants = companyService.getTenantCompanies();

    return ResponseEntity.ok(
        ApiResponse.success(tenants, "Found " + tenants.size() + " tenant companies"));
  }

  @GetMapping("/type/{type}")
  public ResponseEntity<ApiResponse<List<CompanyDto>>> getCompaniesByType(
      @PathVariable String type) {
    log.debug("Getting companies by type: {}", type);
    CompanyType companyType = CompanyType.valueOf(type.toUpperCase());
    List<CompanyDto> companies = companyService.getCompaniesByType(companyType);
    return ResponseEntity.ok(ApiResponse.success(companies));
  }

  @GetMapping("/category/{category}")
  public ResponseEntity<ApiResponse<List<CompanyDto>>> getCompaniesByCategory(
      @PathVariable String category) {
    log.debug("Getting companies by category: {}", category);
    CompanyCategory companyCategory = CompanyCategory.valueOf(category.toUpperCase());
    List<CompanyDto> companies = companyService.getCompaniesByCategory(companyCategory);
    return ResponseEntity.ok(ApiResponse.success(companies));
  }

  @PutMapping("/{id}")
  public ResponseEntity<ApiResponse<CompanyDto>> updateCompany(
      @PathVariable UUID id, @Valid @RequestBody UpdateCompanyRequest request) {
    log.info("Updating company: id={}", id);

    CompanyDto updated = companyService.updateCompany(id, request);

    return ResponseEntity.ok(ApiResponse.success(updated, "Company updated successfully"));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<ApiResponse<Void>> deactivateCompany(@PathVariable UUID id) {
    log.info("Deactivating company: id={}", id);
    companyService.deactivateCompany(id);
    return ResponseEntity.ok(ApiResponse.success(null, "Company deactivated successfully"));
  }
}
