package com.fabricmanagement.common.platform.company.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.platform.company.domain.Company;
import com.fabricmanagement.common.platform.company.domain.CompanyType;
import com.fabricmanagement.common.platform.company.domain.exception.CircularHierarchyException;
import com.fabricmanagement.common.platform.company.domain.exception.HierarchyDepthExceededException;
import com.fabricmanagement.common.platform.company.dto.CompanyDto;
import com.fabricmanagement.common.platform.company.infra.repository.CompanyRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("CompanyHierarchyService")
class CompanyHierarchyServiceTest {

  private static final UUID TENANT_ID = UUID.randomUUID();
  private static final UUID COMPANY_A = UUID.randomUUID();
  private static final UUID COMPANY_B = UUID.randomUUID();
  private static final UUID COMPANY_C = UUID.randomUUID();
  private static final UUID COMPANY_D = UUID.randomUUID();

  @Mock private CompanyRepository companyRepository;

  @InjectMocks private CompanyHierarchyService service;

  @BeforeEach
  void setUp() {
    TenantContext.setCurrentTenantId(TENANT_ID);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  private Company company(UUID id, UUID parentId, boolean active) {
    Company c = new Company();
    c.setId(id);
    c.setTenantId(TENANT_ID);
    c.setParentCompanyId(parentId);
    c.setIsActive(active);
    c.setCompanyName("Company-" + id.toString().substring(0, 8));
    c.setTaxId("tax-" + id.toString().substring(0, 8));
    c.setCompanyType(CompanyType.SPINNER);
    return c;
  }

  @Nested
  @DisplayName("validateParent")
  class ValidateParent {

    @Test
    void throwsWhenTenantContextMissing() {
      TenantContext.clear();

      assertThatThrownBy(() -> service.validateParent(COMPANY_A))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Tenant context must be set");
    }

    @Test
    void throwsWhenParentNotFound() {
      when(companyRepository.findById(COMPANY_A)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> service.validateParent(COMPANY_A))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Parent company not found");
    }

    @Test
    void throwsWhenParentDifferentTenant() {
      Company parent = company(COMPANY_A, null, true);
      parent.setTenantId(UUID.randomUUID());
      when(companyRepository.findById(COMPANY_A)).thenReturn(Optional.of(parent));

      assertThatThrownBy(() -> service.validateParent(COMPANY_A))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("different tenant");
    }

    @Test
    void throwsWhenParentInactive() {
      Company parent = company(COMPANY_A, null, false);
      when(companyRepository.findById(COMPANY_A)).thenReturn(Optional.of(parent));

      assertThatThrownBy(() -> service.validateParent(COMPANY_A))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("must be active");
    }

    @Test
    void throwsWhenDepthWouldExceedMax() {
      // Chain: A (root) -> B -> C. So C has depth 3. Validating C as parent: depth(C)=3 >= MAX(3)
      // -> throw.
      Company companyA = company(COMPANY_A, null, true);
      Company companyB = company(COMPANY_B, COMPANY_A, true);
      Company companyC = company(COMPANY_C, COMPANY_B, true);
      when(companyRepository.findById(COMPANY_C)).thenReturn(Optional.of(companyC));
      when(companyRepository.findById(COMPANY_B)).thenReturn(Optional.of(companyB));
      when(companyRepository.findById(COMPANY_A)).thenReturn(Optional.of(companyA));

      assertThatThrownBy(() -> service.validateParent(COMPANY_C))
          .isInstanceOf(HierarchyDepthExceededException.class)
          .hasMessageContaining("Maximum hierarchy depth");
    }

    @Test
    void passesWhenParentValidAndDepthUnderLimit() {
      Company parent = company(COMPANY_A, null, true);
      when(companyRepository.findById(COMPANY_A)).thenReturn(Optional.of(parent));

      service.validateParent(COMPANY_A);
      // no exception
    }
  }

  @Nested
  @DisplayName("validateNoCircularReference")
  class ValidateNoCircularReference {

    @Test
    void throwsWhenCompanyIsOwnParent() {
      assertThatThrownBy(() -> service.validateNoCircularReference(COMPANY_A, COMPANY_A))
          .isInstanceOf(CircularHierarchyException.class)
          .hasMessageContaining("cannot be its own parent");
    }

    @Test
    void throwsWhenNewParentIsDescendantOfCompany() {
      // B is descendant of A (B -> A). Setting A's parent to B would create cycle A -> B -> A.
      Company companyB = company(COMPANY_B, COMPANY_A, true);
      when(companyRepository.findByTenantIdAndId(TENANT_ID, COMPANY_B))
          .thenReturn(Optional.of(companyB));

      assertThatThrownBy(() -> service.validateNoCircularReference(COMPANY_A, COMPANY_B))
          .isInstanceOf(CircularHierarchyException.class)
          .hasMessageContaining("circular reference");
    }

    @Test
    void passesWhenNoCycle() {
      Company companyA = company(COMPANY_A, null, true);
      when(companyRepository.findByTenantIdAndId(TENANT_ID, COMPANY_A))
          .thenReturn(Optional.of(companyA));

      service.validateNoCircularReference(COMPANY_B, COMPANY_A);
      // no exception: A is not a descendant of B (A has no parent, so no cycle)
    }
  }

  @Nested
  @DisplayName("calculateDepth")
  class CalculateDepth {

    @Test
    void returns1WhenNoParent() {
      Company c = company(COMPANY_A, null, true);
      when(companyRepository.findById(COMPANY_A)).thenReturn(Optional.of(c));

      assertThat(service.calculateDepth(COMPANY_A)).isEqualTo(1);
    }

    @Test
    void returnsChainLength() {
      Company c = company(COMPANY_C, COMPANY_B, true);
      Company b = company(COMPANY_B, COMPANY_A, true);
      Company a = company(COMPANY_A, null, true);
      when(companyRepository.findById(COMPANY_C)).thenReturn(Optional.of(c));
      when(companyRepository.findById(COMPANY_B)).thenReturn(Optional.of(b));
      when(companyRepository.findById(COMPANY_A)).thenReturn(Optional.of(a));

      assertThat(service.calculateDepth(COMPANY_C)).isEqualTo(3);
    }
  }

  @Nested
  @DisplayName("isDescendantOf")
  class IsDescendantOf {

    @Test
    void returnsTrueWhenSameCompany() {
      assertThat(service.isDescendantOf(COMPANY_A, COMPANY_A)).isTrue();
    }

    @Test
    void returnsTrueWhenAncestorInChain() {
      Company child = company(COMPANY_B, COMPANY_A, true);
      when(companyRepository.findByTenantIdAndId(TENANT_ID, COMPANY_B))
          .thenReturn(Optional.of(child));

      assertThat(service.isDescendantOf(COMPANY_B, COMPANY_A)).isTrue();
    }

    @Test
    void returnsFalseWhenNotInChain() {
      Company child = company(COMPANY_B, COMPANY_A, true);
      when(companyRepository.findByTenantIdAndId(TENANT_ID, COMPANY_B))
          .thenReturn(Optional.of(child));
      when(companyRepository.findByTenantIdAndId(TENANT_ID, COMPANY_A))
          .thenReturn(Optional.empty());

      assertThat(service.isDescendantOf(COMPANY_B, COMPANY_D)).isFalse();
    }
  }

  @Nested
  @DisplayName("getChildren")
  class GetChildren {

    @Test
    void returnsDirectChildrenOnly() {
      Company child1 = company(COMPANY_B, COMPANY_A, true);
      Company child2 = company(COMPANY_C, COMPANY_A, true);
      when(companyRepository.findByTenantIdAndParentCompanyId(TENANT_ID, COMPANY_A))
          .thenReturn(List.of(child1, child2));

      List<CompanyDto> children = service.getChildren(COMPANY_A);

      assertThat(children).hasSize(2);
      assertThat(children.stream().map(CompanyDto::getId))
          .containsExactlyInAnyOrder(COMPANY_B, COMPANY_C);
    }
  }

  @Nested
  @DisplayName("getAncestors")
  class GetAncestors {

    @Test
    void returnsEmptyWhenNoParent() {
      Company c = company(COMPANY_A, null, true);
      when(companyRepository.findByTenantIdAndId(TENANT_ID, COMPANY_A)).thenReturn(Optional.of(c));

      List<CompanyDto> ancestors = service.getAncestors(COMPANY_A);

      assertThat(ancestors).isEmpty();
    }

    @Test
    void returnsChainFromParentToRoot() {
      Company c = company(COMPANY_C, COMPANY_B, true);
      Company b = company(COMPANY_B, COMPANY_A, true);
      Company a = company(COMPANY_A, null, true);
      when(companyRepository.findByTenantIdAndId(TENANT_ID, COMPANY_C)).thenReturn(Optional.of(c));
      when(companyRepository.findByTenantIdAndId(TENANT_ID, COMPANY_B)).thenReturn(Optional.of(b));
      when(companyRepository.findByTenantIdAndId(TENANT_ID, COMPANY_A)).thenReturn(Optional.of(a));

      List<CompanyDto> ancestors = service.getAncestors(COMPANY_C);

      assertThat(ancestors).hasSize(2);
      assertThat(ancestors.get(0).getId()).isEqualTo(COMPANY_B);
      assertThat(ancestors.get(1).getId()).isEqualTo(COMPANY_A);
    }
  }
}
