package com.fabricmanagement.production.masterdata.color.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.production.masterdata.color.app.port.TradingPartnerQueryPort;
import com.fabricmanagement.production.masterdata.color.domain.Color;
import com.fabricmanagement.production.masterdata.color.domain.ColorPartnerCode;
import com.fabricmanagement.production.masterdata.color.domain.ColorPartnerRef;
import com.fabricmanagement.production.masterdata.color.domain.PartnerRole;
import com.fabricmanagement.production.masterdata.color.domain.exception.ColorPartnerRefDomainException;
import com.fabricmanagement.production.masterdata.color.dto.AddColorPartnerCodeRequest;
import com.fabricmanagement.production.masterdata.color.dto.ColorPartnerCodeInput;
import com.fabricmanagement.production.masterdata.color.dto.CreateColorPartnerRefRequest;
import com.fabricmanagement.production.masterdata.color.dto.ReactivateColorPartnerRefRequest;
import com.fabricmanagement.production.masterdata.color.dto.UpdateColorPartnerCodeRequest;
import com.fabricmanagement.production.masterdata.color.dto.UpdateColorPartnerRefRequest;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ColorPartnerRefTenantIsolationIT {

  private static final UUID TENANT_A = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa1");
  private static final UUID TENANT_B = UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbb2");

  @Container
  @SuppressWarnings("resource")
  static PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>(DockerImageName.parse("postgres:16.2-alpine"))
          .withDatabaseName("fabric_test")
          .withUsername("fabric_owner")
          .withPassword("fabric123");

  @DynamicPropertySource
  static void registerPgProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
    registry.add("spring.flyway.url", postgres::getJdbcUrl);
    registry.add("spring.flyway.user", postgres::getUsername);
    registry.add("spring.flyway.password", postgres::getPassword);
    registry.add("spring.flyway.enabled", () -> "true");
  }

  @Autowired private ColorService colorService;
  @Autowired private ColorPartnerRefService refService;
  @Autowired private ColorPartnerRefQueryService queryService;

  @MockBean private TradingPartnerQueryPort tradingPartnerQueryPort;

  private UUID partnerId;

  @BeforeEach
  void setUp() {
    partnerId = UUID.randomUUID();
    when(tradingPartnerQueryPort.isActiveAndCompatible(any(), any(), any())).thenReturn(true);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void tenantBSeesNoTenantAListLookupOrMutationSurface() {
    TenantContext.setCurrentTenantId(TENANT_A);
    Color color = colorService.create("TENANT-A-NAVY", "Tenant A navy", "#1F2A44");
    ColorPartnerRef ref = refService.create(color.getId(), createRequest("A-CODE"));
    UUID codeId = ref.primaryCode().getId();

    TenantContext.setCurrentTenantId(TENANT_B);

    assertThatThrownBy(() -> queryService.list(color.getId(), PageRequest.of(0, 20)))
        .isInstanceOf(NotFoundException.class);
    assertThatThrownBy(() -> queryService.resolve(partnerId, PartnerRole.CUSTOMER, "A-CODE"))
        .isInstanceOf(NotFoundException.class);
    assertThatThrownBy(() -> queryService.forward(color.getId(), partnerId, PartnerRole.CUSTOMER))
        .isInstanceOf(NotFoundException.class);
    assertThatThrownBy(
            () ->
                refService.update(
                    color.getId(), ref.getId(), new UpdateColorPartnerRefRequest(BigDecimal.ONE)))
        .isInstanceOf(NotFoundException.class);
    assertThatThrownBy(() -> refService.deactivateCode(color.getId(), ref.getId(), codeId))
        .isInstanceOf(NotFoundException.class);
    assertThatThrownBy(() -> refService.deactivate(color.getId(), ref.getId()))
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  void inactiveColorBlocksLookupsAndNonDeactivationWritesWithoutReleasingMappings() {
    TenantContext.setCurrentTenantId(TENANT_A);
    Color color = colorService.create("LIFECYCLE-NAVY", "Lifecycle navy", "#1F2A44");
    ColorPartnerRef ref = refService.create(color.getId(), createRequest("PRIMARY"));
    ColorPartnerCode alias =
        refService.addCode(
            color.getId(), ref.getId(), new AddColorPartnerCodeRequest("ALIAS", null));
    UUID aliasId = alias.getId();
    assertThat(aliasId).isNotNull();

    colorService.deactivate(color.getId());

    assertThatThrownBy(() -> queryService.resolve(partnerId, PartnerRole.CUSTOMER, "ALIAS"))
        .isInstanceOf(NotFoundException.class);
    assertThatThrownBy(() -> queryService.forward(color.getId(), partnerId, PartnerRole.CUSTOMER))
        .isInstanceOf(NotFoundException.class);
    assertThatThrownBy(
            () ->
                refService.update(
                    color.getId(), ref.getId(), new UpdateColorPartnerRefRequest(BigDecimal.ONE)))
        .isInstanceOf(NotFoundException.class);
    assertThatThrownBy(
            () ->
                refService.updateCode(
                    color.getId(),
                    ref.getId(),
                    aliasId,
                    new UpdateColorPartnerCodeRequest("Renamed")))
        .isInstanceOf(NotFoundException.class);

    ColorPartnerRef inactive = refService.deactivate(color.getId(), ref.getId());
    assertThat(inactive.getCodes()).allMatch(code -> !Boolean.TRUE.equals(code.getIsActive()));
    assertThatThrownBy(
            () ->
                refService.reactivate(
                    color.getId(),
                    ref.getId(),
                    new ReactivateColorPartnerRefRequest(aliasId, null)))
        .isInstanceOf(NotFoundException.class);

    colorService.activate(color.getId());
    refService.reactivate(
        color.getId(), ref.getId(), new ReactivateColorPartnerRefRequest(aliasId, null));

    assertThat(queryService.resolve(partnerId, PartnerRole.CUSTOMER, "ALIAS").color().id())
        .isEqualTo(color.getId());
  }

  @Test
  void inactiveReferenceRejectsEveryMutationExceptReactivation() {
    TenantContext.setCurrentTenantId(TENANT_A);
    Color color = colorService.create("REF-LIFECYCLE", "Reference lifecycle", "#1F2A44");
    ColorPartnerRef ref = refService.create(color.getId(), createRequest("PRIMARY"));
    UUID primaryId = ref.primaryCode().getId();
    refService.deactivate(color.getId(), ref.getId());

    assertThatThrownBy(
            () ->
                refService.addCode(
                    color.getId(), ref.getId(), new AddColorPartnerCodeRequest("ALIAS", null)))
        .isInstanceOf(ColorPartnerRefDomainException.class);
    assertThatThrownBy(
            () ->
                refService.update(
                    color.getId(), ref.getId(), new UpdateColorPartnerRefRequest(null)))
        .isInstanceOf(ColorPartnerRefDomainException.class);
    assertThatThrownBy(() -> refService.deactivate(color.getId(), ref.getId()))
        .isInstanceOf(ColorPartnerRefDomainException.class);

    ColorPartnerRef reactivated =
        refService.reactivate(
            color.getId(), ref.getId(), new ReactivateColorPartnerRefRequest(primaryId, null));
    assertThat(reactivated.getIsActive()).isTrue();
  }

  private CreateColorPartnerRefRequest createRequest(String externalCode) {
    return new CreateColorPartnerRefRequest(
        partnerId, PartnerRole.CUSTOMER, null, new ColorPartnerCodeInput(externalCode, null));
  }
}
