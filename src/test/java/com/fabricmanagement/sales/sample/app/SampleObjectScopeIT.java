package com.fabricmanagement.sales.sample.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.security.AuthenticatedUserContext;
import com.fabricmanagement.common.infrastructure.security.PermissionEvaluator;
import com.fabricmanagement.common.infrastructure.security.dto.PermissionResult;
import com.fabricmanagement.platform.user.app.UserQueryService;
import com.fabricmanagement.platform.user.app.UserQueryService.PermissionIdentity;
import com.fabricmanagement.platform.user.domain.DataScope;
import com.fabricmanagement.sales.sample.domain.DeliveryMethod;
import com.fabricmanagement.sales.sample.domain.SampleDelivery;
import com.fabricmanagement.sales.sample.domain.SampleRequest;
import com.fabricmanagement.sales.sample.domain.SampleRequestStatus;
import com.fabricmanagement.sales.sample.infra.repository.SampleDeliveryRepository;
import com.fabricmanagement.sales.sample.infra.repository.SampleRequestRepository;
import com.fabricmanagement.testsupport.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@AutoConfigureMockMvc
class SampleObjectScopeIT extends AbstractIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private SampleRequestRepository requests;
  @Autowired private SampleDeliveryRepository deliveries;
  @Autowired private CacheManager cacheManager;

  @MockitoBean private PermissionEvaluator permissionEvaluator;
  @MockitoBean private UserQueryService userQueryService;

  private final UUID tenantId = UUID.randomUUID();
  private final UUID otherTenantId = UUID.randomUUID();
  private final UUID ownerId = UUID.randomUUID();
  private final UUID strangerId = UUID.randomUUID();
  private final Map<UUID, Map<String, DataScope>> permissions = new HashMap<>();
  private SampleRequest requestToDispatch;
  private SampleRequest dispatchedRequest;
  private SampleDelivery deliveryCreatedByStranger;

  @BeforeEach
  void setUp() {
    reset(permissionEvaluator, userQueryService);
    permissions.clear();
    when(userQueryService.findPermissionIdentity(any(), any()))
        .thenAnswer(
            invocation -> {
              UUID requestedTenant = invocation.getArgument(0);
              UUID userId = invocation.getArgument(1);
              if (!tenantId.equals(requestedTenant)
                  || (!ownerId.equals(userId) && !strangerId.equals(userId))) {
                return Optional.empty();
              }
              return Optional.of(new PermissionIdentity("WORKER", List.of("SALES")));
            });
    when(permissionEvaluator.evaluate(any(), any(), any(), any()))
        .thenAnswer(
            invocation -> {
              UUID userId = invocation.getArgument(3);
              Map<String, DataScope> actions = permissions.getOrDefault(userId, Map.of());
              return actions.isEmpty()
                  ? new PermissionResult(Map.of(), false)
                  : new PermissionResult(Map.of("sales", actions), false);
            });

    requestToDispatch = persistRequest(ownerId, SampleRequestStatus.REQUESTED);
    dispatchedRequest = persistRequest(ownerId, SampleRequestStatus.DISPATCHED);
    deliveryCreatedByStranger = persistDelivery(dispatchedRequest, strangerId);
    TenantContext.clear();
  }

  @AfterEach
  void clearContext() {
    TenantContext.clear();
  }

  @Test
  void listAndDetailUseTheRequestCreatorScopeInTheDatabase() throws Exception {
    grant(ownerId, "read", DataScope.OWN);
    performAs(ownerId, get("/api/v1/sales/samples?page=0&size=20"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(2));
    performAs(ownerId, get("/api/v1/sales/samples/requests/{id}", requestToDispatch.getId()))
        .andExpect(status().isOk());

    grant(strangerId, "read", DataScope.OWN);
    performAs(strangerId, get("/api/v1/sales/samples?page=0&size=20"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(0));
    performAs(strangerId, get("/api/v1/sales/samples/requests/{id}", requestToDispatch.getId()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("ENTITY_NOT_FOUND"));
  }

  @Test
  void dispatchAndDeliveryCompletionAuthorizeTheParentBeforeMutation() throws Exception {
    grant(strangerId, "write", DataScope.OWN);
    performAs(
            strangerId,
            post("/api/v1/sales/samples/requests/{id}/dispatch", requestToDispatch.getId())
                .content(json(Map.of("deliveryMethod", "CARGO"))))
        .andExpect(status().isForbidden());
    assertThat(reloadRequest(requestToDispatch.getId()).getStatus())
        .isEqualTo(SampleRequestStatus.REQUESTED);

    performAs(
            strangerId,
            post(
                    "/api/v1/sales/samples/deliveries/{id}/mark-delivered",
                    deliveryCreatedByStranger.getId())
                .content(json(Map.of("recipientName", "Blocked recipient"))))
        .andExpect(status().isForbidden());
    assertThat(reloadDelivery(deliveryCreatedByStranger.getId()).getDeliveredAt()).isNull();
    assertThat(reloadRequest(dispatchedRequest.getId()).getStatus())
        .isEqualTo(SampleRequestStatus.DISPATCHED);

    grant(ownerId, "write", DataScope.OWN);
    performAs(
            ownerId,
            post(
                    "/api/v1/sales/samples/deliveries/{id}/mark-delivered",
                    deliveryCreatedByStranger.getId())
                .content(json(Map.of("recipientName", "Accepted recipient"))))
        .andExpect(status().isOk());
    assertThat(reloadDelivery(deliveryCreatedByStranger.getId()).getDeliveredAt()).isNotNull();
    assertThat(reloadRequest(dispatchedRequest.getId()).getStatus())
        .isEqualTo(SampleRequestStatus.DELIVERED);
  }

  @Test
  void crossTenantRequestAndDeliveryIdsAreNotResolvedOrMutated() throws Exception {
    SampleRequest otherRequest =
        persistRequest(otherTenantId, ownerId, SampleRequestStatus.REQUESTED);
    SampleRequest otherDispatched =
        persistRequest(otherTenantId, ownerId, SampleRequestStatus.DISPATCHED);
    SampleDelivery otherDelivery = persistDelivery(otherTenantId, otherDispatched, strangerId);
    grant(ownerId, "write", DataScope.ORGANIZATION);

    performAs(
            ownerId,
            post("/api/v1/sales/samples/requests/{id}/dispatch", otherRequest.getId())
                .content(json(Map.of("deliveryMethod", "CARGO"))))
        .andExpect(status().isNotFound());
    performAs(
            ownerId,
            post("/api/v1/sales/samples/deliveries/{id}/mark-delivered", otherDelivery.getId())
                .content(json(Map.of("recipientName", "Cross tenant"))))
        .andExpect(status().isNotFound());

    assertThat(reloadRequest(otherTenantId, otherRequest.getId()).getStatus())
        .isEqualTo(SampleRequestStatus.REQUESTED);
    assertThat(reloadDelivery(otherTenantId, otherDelivery.getId()).getDeliveredAt()).isNull();
  }

  private ResultActions performAs(UUID userId, MockHttpServletRequestBuilder request)
      throws Exception {
    TenantContext.setCurrentTenantId(tenantId);
    TenantContext.setCurrentUserId(userId);
    AuthenticatedUserContext context =
        new AuthenticatedUserContext(userId, "WORKER", List.of("SALES"), null, tenantId);
    var token = UsernamePasswordAuthenticationToken.authenticated(context, "n/a", List.of());
    token.setDetails(context);
    return mockMvc.perform(
        request.with(authentication(token)).with(csrf()).contentType(MediaType.APPLICATION_JSON));
  }

  private String json(Object value) throws Exception {
    return objectMapper.writeValueAsString(value);
  }

  private void grant(UUID userId, String action, DataScope scope) {
    permissions.computeIfAbsent(userId, ignored -> new HashMap<>()).put(action, scope);
    Cache cache = cacheManager.getCache("permissions");
    if (cache != null) {
      cache.clear();
    }
  }

  private SampleRequest persistRequest(UUID createdBy, SampleRequestStatus status) {
    return persistRequest(tenantId, createdBy, status);
  }

  private SampleRequest persistRequest(
      UUID targetTenantId, UUID createdBy, SampleRequestStatus status) {
    TenantContext.setCurrentTenantId(targetTenantId);
    TenantContext.setCurrentUserId(createdBy);
    SampleRequest request = new SampleRequest();
    request.setTenantId(targetTenantId);
    request.setCreatedBy(createdBy);
    request.setCustomerId(UUID.randomUUID());
    request.setProductId(UUID.randomUUID());
    request.setRequestedQty(new BigDecimal("2.000"));
    request.setUnit("M");
    request.setDeliveryMethod(DeliveryMethod.CARGO);
    request.setStatus(status);
    return requests.saveAndFlush(request);
  }

  private SampleDelivery persistDelivery(SampleRequest request, UUID createdBy) {
    return persistDelivery(tenantId, request, createdBy);
  }

  private SampleDelivery persistDelivery(
      UUID targetTenantId, SampleRequest request, UUID createdBy) {
    TenantContext.setCurrentTenantId(targetTenantId);
    TenantContext.setCurrentUserId(createdBy);
    SampleDelivery delivery = new SampleDelivery();
    delivery.setTenantId(targetTenantId);
    delivery.setCreatedBy(createdBy);
    delivery.setSampleRequestId(request.getId());
    delivery.setDeliveryMethod(DeliveryMethod.CARGO);
    delivery.setDispatchedAt(Instant.now());
    return deliveries.saveAndFlush(delivery);
  }

  private SampleRequest reloadRequest(UUID requestId) {
    return reloadRequest(tenantId, requestId);
  }

  private SampleRequest reloadRequest(UUID targetTenantId, UUID requestId) {
    TenantContext.setCurrentTenantId(targetTenantId);
    return requests.findByTenantIdAndIdAndIsActiveTrue(targetTenantId, requestId).orElseThrow();
  }

  private SampleDelivery reloadDelivery(UUID deliveryId) {
    return reloadDelivery(tenantId, deliveryId);
  }

  private SampleDelivery reloadDelivery(UUID targetTenantId, UUID deliveryId) {
    TenantContext.setCurrentTenantId(targetTenantId);
    return deliveries.findByTenantIdAndIdAndIsActiveTrue(targetTenantId, deliveryId).orElseThrow();
  }
}
