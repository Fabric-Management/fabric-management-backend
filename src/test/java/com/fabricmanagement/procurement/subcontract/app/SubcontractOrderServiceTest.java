package com.fabricmanagement.procurement.subcontract.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.DocumentNumberGenerator;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.PagedResponse;
import com.fabricmanagement.procurement.subcontract.domain.SubcontractOrder;
import com.fabricmanagement.procurement.subcontract.domain.SubcontractOrderStatus;
import com.fabricmanagement.procurement.subcontract.dto.SubcontractOrderResponse;
import com.fabricmanagement.procurement.subcontract.infra.repository.SubcontractOrderRepository;
import com.fabricmanagement.production.masterdata.product.api.facade.ProductFacade;
import com.fabricmanagement.production.masterdata.product.domain.ProductType;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

@ExtendWith(MockitoExtension.class)
class SubcontractOrderServiceTest {

  @Mock private SubcontractOrderRepository scRepository;
  @Mock private ProductFacade productFacade;
  @Mock private DocumentNumberGenerator documentNumberGenerator;

  @InjectMocks private SubcontractOrderService subcontractOrderService;

  private final UUID tenantId = UUID.randomUUID();
  private SubcontractOrder subcontractOrder;

  @BeforeEach
  void setUp() {
    TenantContext.setCurrentTenantId(tenantId);

    subcontractOrder =
        SubcontractOrder.create(
            tenantId,
            "SC-20260624-00001",
            UUID.randomUUID(),
            null,
            UUID.randomUUID(),
            UUID.randomUUID(),
            ProductType.FABRIC,
            UUID.randomUUID(),
            ProductType.FABRIC,
            new BigDecimal("95.000"),
            "KG",
            new BigDecimal("100.000"),
            "KG",
            new BigDecimal("12.5000"),
            "USD",
            LocalDate.now().plusDays(7),
            "Dyeing subcontract");
    subcontractOrder.setStatus(SubcontractOrderStatus.CONFIRMED);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  @DisplayName("Should list subcontract orders as a paged response")
  void shouldListSubcontractOrders() {
    Pageable pageable = PageRequest.of(0, 20);
    when(scRepository.findAll(any(Specification.class), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(subcontractOrder), pageable, 1));

    PagedResponse<SubcontractOrderResponse> response =
        subcontractOrderService.listSubcontractOrders(null, pageable);

    assertEquals(1, response.getContent().size());
    assertEquals("SC-20260624-00001", response.getContent().get(0).getScNumber());
    assertEquals(SubcontractOrderStatus.CONFIRMED, response.getContent().get(0).getStatus());
    assertEquals(0, response.getPage());
    assertEquals(20, response.getSize());
  }

  @Test
  @DisplayName("Should build status filter when listing subcontract orders")
  @SuppressWarnings({"unchecked", "rawtypes"})
  void shouldBuildStatusFilterWhenListingSubcontractOrders() {
    Pageable pageable = PageRequest.of(0, 20);
    when(scRepository.findAll(any(Specification.class), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(subcontractOrder), pageable, 1));

    subcontractOrderService.listSubcontractOrders(SubcontractOrderStatus.CONFIRMED, pageable);

    ArgumentCaptor<Specification<SubcontractOrder>> specCaptor =
        ArgumentCaptor.forClass(Specification.class);
    verify(scRepository).findAll(specCaptor.capture(), any(Pageable.class));

    Root root = org.mockito.Mockito.mock(Root.class);
    CriteriaQuery query = org.mockito.Mockito.mock(CriteriaQuery.class);
    CriteriaBuilder cb = org.mockito.Mockito.mock(CriteriaBuilder.class);
    Path tenantPath = org.mockito.Mockito.mock(Path.class);
    Path activePath = org.mockito.Mockito.mock(Path.class);
    Path statusPath = org.mockito.Mockito.mock(Path.class);
    Predicate tenantPredicate = org.mockito.Mockito.mock(Predicate.class);
    Predicate activePredicate = org.mockito.Mockito.mock(Predicate.class);
    Predicate statusPredicate = org.mockito.Mockito.mock(Predicate.class);
    Predicate combinedPredicate = org.mockito.Mockito.mock(Predicate.class);

    when(root.get("tenantId")).thenReturn(tenantPath);
    when(root.get("isActive")).thenReturn(activePath);
    when(root.get("status")).thenReturn(statusPath);
    when(cb.equal(tenantPath, tenantId)).thenReturn(tenantPredicate);
    when(cb.isTrue(activePath)).thenReturn(activePredicate);
    when(cb.equal(statusPath, SubcontractOrderStatus.CONFIRMED)).thenReturn(statusPredicate);
    when(cb.and(any(Predicate[].class))).thenReturn(combinedPredicate);

    Predicate predicate = specCaptor.getValue().toPredicate(root, query, cb);

    assertEquals(combinedPredicate, predicate);
    verify(cb).equal(statusPath, SubcontractOrderStatus.CONFIRMED);
  }
}
