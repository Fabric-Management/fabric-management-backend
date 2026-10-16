package com.fabricmanagement.platform.user.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.platform.common.exception.PlatformDomainException;
import com.fabricmanagement.platform.organization.domain.Organization;
import com.fabricmanagement.platform.organization.domain.OrganizationType;
import com.fabricmanagement.platform.organization.infra.repository.OrganizationRepository;
import com.fabricmanagement.platform.tenant.domain.Tenant;
import com.fabricmanagement.platform.tenant.infra.repository.TenantRepository;
import com.fabricmanagement.platform.user.domain.User;
import com.fabricmanagement.platform.user.dto.UiPreferenceRequest;
import com.fabricmanagement.platform.user.dto.UiPreferenceResponse;
import com.fabricmanagement.platform.user.infra.repository.UserRepository;
import com.fabricmanagement.testsupport.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/** No test-wide transaction: each save commits like a request does. */
@AutoConfigureMockMvc(addFilters = false)
@WithMockUser
class UserUiPreferenceIntegrationTest extends AbstractIntegrationTest {

  private static final String KEY = "sales.new-order.sections";

  @Autowired private UserUiPreferenceService service;
  @Autowired private TenantRepository tenants;
  @Autowired private OrganizationRepository organizations;
  @Autowired private UserRepository users;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  private Identity identity;

  @BeforeEach
  void setUp() {
    identity = newIdentity();
    useIdentity(identity);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void aKeyNeverSavedReadsAsAnEmptyListInsideApiResponse() throws Exception {
    mockMvc
        .perform(get("/api/v1/common/users/{id}/ui-preferences/{key}", identity.userId(), KEY))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.key").value(KEY))
        .andExpect(jsonPath("$.data.values").isEmpty());

    useIdentity(identity); // JwtContextInterceptor clears the request's context after completion.
    assertThat(rowCount(identity)).isZero();
  }

  @Test
  void putCreatesThePreferenceAndASecondPutReplacesItInTheSameRow() throws Exception {
    mockMvc
        .perform(
            put("/api/v1/common/users/{id}/ui-preferences/{key}", identity.userId(), KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("items", "customer", "terms", "payment")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.key").value(KEY))
        .andExpect(jsonPath("$.data.values[0]").value("items"))
        .andExpect(jsonPath("$.data.values[3]").value("payment"));
    useIdentity(identity);
    var created = row(identity);

    var replaced =
        service.savePreference(
            identity.tenantId(), identity.userId(), KEY, List.of("payment", "items"));

    assertThat(replaced).isEqualTo(new UiPreferenceResponse(KEY, List.of("payment", "items")));
    assertThat(service.getPreference(identity.tenantId(), identity.userId(), KEY))
        .isEqualTo(replaced);
    var updated = row(identity);
    assertThat(rowCount(identity)).isEqualTo(1);
    assertThat(updated)
        .containsEntry("id", created.get("id"))
        .containsEntry("uid", created.get("uid"))
        .containsEntry("created_at", created.get("created_at"))
        .containsEntry("version", 1L);
  }

  @Test
  void theUpsertPopulatesTheIdentifierAndAuditColumnsOfBaseEntity() {
    service.savePreference(identity.tenantId(), identity.userId(), KEY, List.of("items"));
    var stored = row(identity);

    assertThat(stored.get("id")).isInstanceOf(UUID.class);
    assertThat(stored.get("uid").toString()).matches(identity.tenantUid() + "-UIPREF-[0-9A-F]{8}");
    assertThat(stored.get("created_at")).isNotNull();
    assertThat(stored.get("updated_at")).isNotNull();
    assertThat(stored)
        .containsEntry("tenant_id", identity.tenantId())
        .containsEntry("user_id", identity.userId())
        .containsEntry("preference_key", KEY)
        .containsEntry("created_by", identity.userId())
        .containsEntry("updated_by", identity.userId())
        .containsEntry("is_active", true)
        .containsEntry("deleted_at", null)
        .containsEntry("version", 0L);
  }

  @Test
  void keysAreKeptApartAndAnEmptyListClearsOnlyItsOwnKey() {
    service.savePreference(identity.tenantId(), identity.userId(), KEY, List.of("items"));
    service.savePreference(
        identity.tenantId(), identity.userId(), "sales.quote.sections", List.of("payment"));

    service.savePreference(identity.tenantId(), identity.userId(), KEY, List.of());

    assertThat(service.getPreference(identity.tenantId(), identity.userId(), KEY).values())
        .isEmpty();
    assertThat(
            service
                .getPreference(identity.tenantId(), identity.userId(), "sales.quote.sections")
                .values())
        .containsExactly("payment");
    assertThat(rowCount(identity)).isEqualTo(2);
  }

  @Test
  void invalidKeysAndValuesAreRejectedWithoutWriting() throws Exception {
    for (String key : List.of("sections", "Sales.Order", "sales..order", "sales.order.")) {
      assertThatThrownBy(
              () -> service.savePreference(identity.tenantId(), identity.userId(), key, List.of()))
          .isInstanceOf(PlatformDomainException.class);
    }
    List<List<String>> invalidValues =
        List.of(
            Collections.singletonList(null),
            List.of(" "),
            List.of("a".repeat(101)),
            List.of("items", "items"),
            IntStream.range(0, 51).mapToObj(i -> "v" + i).toList());
    for (List<String> values : invalidValues) {
      assertThatThrownBy(
              () -> service.savePreference(identity.tenantId(), identity.userId(), KEY, values))
          .isInstanceOf(PlatformDomainException.class);
    }
    mockMvc
        .perform(
            put("/api/v1/common/users/{id}/ui-preferences/{key}", identity.userId(), KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        // Bean validation failures answer 422 across the API (GlobalExceptionHandler).
        .andExpect(status().isUnprocessableEntity());

    useIdentity(identity);
    assertThat(rowCount(identity)).isZero();
  }

  @Test
  void aUserOfAnotherTenantIsNeitherReadNorWritten() {
    var other = newIdentity();
    useIdentity(other);
    service.savePreference(other.tenantId(), other.userId(), KEY, List.of("payment"));
    var before = row(other);

    useIdentity(identity);
    assertThatThrownBy(
            () ->
                service.savePreference(identity.tenantId(), other.userId(), KEY, List.of("items")))
        .isInstanceOf(NotFoundException.class);
    assertThat(service.getPreference(identity.tenantId(), other.userId(), KEY).values()).isEmpty();

    useIdentity(other);
    assertThat(row(other)).isEqualTo(before);
  }

  @Test
  void pathIdMustBeTheAuthenticatedUser() throws Exception {
    var anotherUser = users.saveAndFlush(User.create("Other", "User", identity.organizationId()));
    mockMvc
        .perform(get("/api/v1/common/users/{id}/ui-preferences/{key}", anotherUser.getId(), KEY))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            put("/api/v1/common/users/{id}/ui-preferences/{key}", anotherUser.getId(), KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("items")))
        .andExpect(status().isForbidden());

    useIdentity(identity);
    assertThat(
            rowCount(
                new Identity(
                    identity.tenantId(),
                    identity.tenantUid(),
                    identity.organizationId(),
                    anotherUser.getId())))
        .isZero();
  }

  private String body(String... values) throws Exception {
    return objectMapper.writeValueAsString(new UiPreferenceRequest(List.of(values)));
  }

  private Identity newIdentity() {
    TenantContext.clear();
    String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
    Tenant tenant = Tenant.create("UI preference " + suffix, "UIP-" + suffix);
    tenant.activate("test");
    tenant = tenants.saveAndFlush(tenant);
    TenantContext.setCurrentTenantId(tenant.getId());
    TenantContext.setCurrentTenantUid(tenant.getUid());
    var organization =
        organizations.saveAndFlush(
            Organization.create("UI preference " + suffix, suffix, OrganizationType.VERTICAL_MILL));
    var user = users.saveAndFlush(User.create("Ui", "User", organization.getId()));
    return new Identity(tenant.getId(), tenant.getUid(), organization.getId(), user.getId());
  }

  private void useIdentity(Identity selected) {
    TenantContext.setCurrentTenantId(selected.tenantId());
    TenantContext.setCurrentTenantUid(selected.tenantUid());
    TenantContext.setCurrentUserId(selected.userId());
  }

  private Map<String, Object> row(Identity selected) {
    return jdbc.queryForMap(
        "SELECT * FROM common_user.user_ui_preference"
            + " WHERE tenant_id = ? AND user_id = ? AND preference_key = ?",
        selected.tenantId(),
        selected.userId(),
        KEY);
  }

  private int rowCount(Identity selected) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM common_user.user_ui_preference WHERE tenant_id = ? AND user_id = ?",
        Integer.class,
        selected.tenantId(),
        selected.userId());
  }

  private record Identity(UUID tenantId, String tenantUid, UUID organizationId, UUID userId) {}
}
