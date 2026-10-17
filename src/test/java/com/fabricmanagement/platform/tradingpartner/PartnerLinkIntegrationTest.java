package com.fabricmanagement.platform.tradingpartner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.platform.common.exception.PlatformDomainException;
import com.fabricmanagement.platform.organization.domain.Organization;
import com.fabricmanagement.platform.organization.domain.OrganizationType;
import com.fabricmanagement.platform.organization.infra.repository.OrganizationRepository;
import com.fabricmanagement.platform.tenant.domain.Tenant;
import com.fabricmanagement.platform.tenant.infra.repository.TenantRepository;
import com.fabricmanagement.platform.tradingpartner.app.PartnerLinkService;
import com.fabricmanagement.platform.tradingpartner.app.TradingPartnerResolver;
import com.fabricmanagement.platform.tradingpartner.app.TradingPartnerService;
import com.fabricmanagement.platform.tradingpartner.domain.PartnerLinkRole;
import com.fabricmanagement.platform.tradingpartner.domain.PartnerMatchReason;
import com.fabricmanagement.platform.tradingpartner.domain.PartnerType;
import com.fabricmanagement.platform.tradingpartner.dto.CreatePartnerLinkRequest;
import com.fabricmanagement.platform.tradingpartner.dto.CreateTradingPartnerRequest;
import com.fabricmanagement.platform.tradingpartner.dto.PartnerLinkDto;
import com.fabricmanagement.platform.tradingpartner.dto.PartnerMatchDto;
import com.fabricmanagement.platform.tradingpartner.dto.QuickCreatePartnerLinkRequest;
import com.fabricmanagement.platform.tradingpartner.dto.QuickCreatePartnerLinkResponse;
import com.fabricmanagement.platform.tradingpartner.dto.TradingPartnerDto;
import com.fabricmanagement.testsupport.AbstractIntegrationTest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * CONSIGNEE-1 S1: companies that receive goods for a customer, linked with a role.
 *
 * <p>Runs in a rolled-back transaction; the database guards are exercised with savepoints so one
 * rejected insert does not abort the rest of the test.
 */
@Transactional
class PartnerLinkIntegrationTest extends AbstractIntegrationTest {

  @Autowired private PartnerLinkService linkService;
  @Autowired private TradingPartnerService partnerService;
  @Autowired private TradingPartnerResolver partnerResolver;
  @Autowired private TenantRepository tenants;
  @Autowired private OrganizationRepository organizations;

  // The context's only JdbcTemplate bean is the system one (separate DataSource, auto-commit): it
  // cannot see this test's uncommitted rows. Build one on the primary DataSource, which joins
  // the test transaction.
  @Autowired
  @Qualifier("dataSource")
  private DataSource dataSource;

  private JdbcTemplate jdbc;
  @PersistenceContext private EntityManager entityManager;

  private Tenant tenant;
  private String suffix;

  @BeforeEach
  void setUp() {
    jdbc = new JdbcTemplate(dataSource);
    suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
    tenant = newTenant("A");
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void linkingAnExistingPartnerCreatesAnActiveLinkListedWithItsRole() {
    TradingPartnerDto customer = partner("Customer", PartnerType.CUSTOMER);
    TradingPartnerDto maker = partner("Maker", PartnerType.SUPPLIER);

    PartnerLinkDto created =
        linkService.link(
            customer.getId(),
            linkRequest(maker.getId(), PartnerLinkRole.GARMENT_MAKER, "  Dock 4 "));

    assertThat(created.customerPartnerId()).isEqualTo(customer.getId());
    assertThat(created.linkedPartnerId()).isEqualTo(maker.getId());
    assertThat(created.role()).isEqualTo(PartnerLinkRole.GARMENT_MAKER);
    assertThat(created.note()).isEqualTo("Dock 4");
    assertThat(created.linkedPartnerName()).isEqualTo("Maker " + suffix);
    assertThat(created.linkedPartnerType()).isEqualTo(PartnerType.SUPPLIER);
    assertThat(linkService.list(customer.getId())).containsExactly(created);
    entityManager.flush();
    assertThat(
            jdbc.queryForObject(
                "SELECT uid FROM common_company.partner_link WHERE id = ?",
                String.class,
                created.id()))
        .contains("-PLNK-");
  }

  @Test
  void theSameCompanyCanServeTwoCustomersAndTwoRoles() {
    TradingPartnerDto first = partner("First customer", PartnerType.CUSTOMER);
    TradingPartnerDto second = partner("Second customer", PartnerType.CUSTOMER);
    TradingPartnerDto company = partner("Shared company", PartnerType.SUPPLIER);

    linkService.link(
        first.getId(), linkRequest(company.getId(), PartnerLinkRole.GARMENT_MAKER, null));
    linkService.link(first.getId(), linkRequest(company.getId(), PartnerLinkRole.DYEHOUSE, null));
    linkService.link(
        second.getId(), linkRequest(company.getId(), PartnerLinkRole.GARMENT_MAKER, null));

    assertThat(linkService.list(first.getId()))
        .extracting(PartnerLinkDto::role)
        .containsExactlyInAnyOrder(PartnerLinkRole.GARMENT_MAKER, PartnerLinkRole.DYEHOUSE);
    assertThat(linkService.list(second.getId())).hasSize(1);
  }

  @Test
  void repeatingAnActiveLinkIsAConflict() {
    TradingPartnerDto customer = partner("Customer", PartnerType.CUSTOMER);
    TradingPartnerDto maker = partner("Maker", PartnerType.SUPPLIER);
    linkService.link(customer.getId(), linkRequest(maker.getId(), PartnerLinkRole.PRINTER, null));

    assertThatThrownBy(
            () ->
                linkService.link(
                    customer.getId(), linkRequest(maker.getId(), PartnerLinkRole.PRINTER, null)))
        .isInstanceOfSatisfying(
            PlatformDomainException.class,
            ex -> {
              assertThat(ex.getErrorCode()).isEqualTo(PartnerLinkService.CODE_DUPLICATE);
              assertThat(ex.getHttpStatus()).isEqualTo(409);
            });
    assertThat(linkService.list(customer.getId())).hasSize(1);
  }

  @Test
  void aPartnerCannotBeLinkedToItself() {
    TradingPartnerDto customer = partner("Customer", PartnerType.CUSTOMER);

    assertThatThrownBy(
            () ->
                linkService.link(
                    customer.getId(),
                    linkRequest(customer.getId(), PartnerLinkRole.WAREHOUSE, null)))
        .isInstanceOfSatisfying(
            PlatformDomainException.class,
            ex -> {
              assertThat(ex.getErrorCode()).isEqualTo(PartnerLinkService.CODE_SELF_LINK);
              assertThat(ex.getHttpStatus()).isEqualTo(400);
            });
  }

  @Test
  void onlyCustomersCanOwnLinks() {
    TradingPartnerDto supplier = partner("Supplier", PartnerType.SUPPLIER);
    TradingPartnerDto company = partner("Company", PartnerType.SERVICE_PROVIDER);

    assertThatThrownBy(
            () ->
                linkService.link(
                    supplier.getId(), linkRequest(company.getId(), PartnerLinkRole.OTHER, null)))
        .isInstanceOfSatisfying(
            PlatformDomainException.class,
            ex ->
                assertThat(ex.getErrorCode()).isEqualTo(PartnerLinkService.CODE_CUSTOMER_REQUIRED));
  }

  @Test
  void unlinkingIsASoftDeleteAndTheSameLinkCanBeCreatedAgain() {
    TradingPartnerDto customer = partner("Customer", PartnerType.CUSTOMER);
    TradingPartnerDto maker = partner("Maker", PartnerType.SUPPLIER);
    PartnerLinkDto first =
        linkService.link(
            customer.getId(), linkRequest(maker.getId(), PartnerLinkRole.FORWARDER, null));

    linkService.unlink(customer.getId(), first.id());

    assertThat(linkService.list(customer.getId())).isEmpty();
    entityManager.flush();
    assertThat(
            jdbc.queryForMap(
                "SELECT is_active, deleted_at FROM common_company.partner_link WHERE id = ?",
                first.id()))
        .containsEntry("is_active", false)
        .doesNotContainEntry("deleted_at", null);
    assertThatThrownBy(() -> linkService.unlink(customer.getId(), first.id()))
        .isInstanceOf(NotFoundException.class);

    PartnerLinkDto again =
        linkService.link(
            customer.getId(), linkRequest(maker.getId(), PartnerLinkRole.FORWARDER, null));

    assertThat(again.id()).isNotEqualTo(first.id());
    assertThat(linkService.list(customer.getId())).containsExactly(again);
    assertThat(linkRows(tenant.getId(), false)).isEqualTo(2);
    assertThat(linkRows(tenant.getId(), true)).isEqualTo(1);
  }

  @Test
  void theDatabaseStillRejectsSelfLinksUnknownRolesAndDuplicatesWhenTheServiceIsBypassed() {
    TradingPartnerDto customer = partner("Customer", PartnerType.CUSTOMER);
    TradingPartnerDto maker = partner("Maker", PartnerType.SUPPLIER);
    linkService.link(customer.getId(), linkRequest(maker.getId(), PartnerLinkRole.DYEHOUSE, null));

    String insert =
        "INSERT INTO common_company.partner_link"
            + " (id, tenant_id, uid, customer_partner_id, linked_partner_id, role)"
            + " VALUES (gen_random_uuid(), ?, ?, ?, ?, ?)";

    assertDatabaseRejects(
        insert,
        tenant.getId(),
        "RAW-" + UUID.randomUUID(),
        customer.getId(),
        customer.getId(),
        "OTHER");
    assertDatabaseRejects(
        insert,
        tenant.getId(),
        "RAW-" + UUID.randomUUID(),
        customer.getId(),
        maker.getId(),
        "BOGUS");
    assertDatabaseRejects(
        insert,
        tenant.getId(),
        "RAW-" + UUID.randomUUID(),
        customer.getId(),
        maker.getId(),
        "DYEHOUSE");
    assertThat(linkRows(tenant.getId(), true)).isEqualTo(1);
  }

  @Test
  void aTenantNeverSeesOrChangesAnotherTenantsLinks() {
    TradingPartnerDto customer = partner("Customer", PartnerType.CUSTOMER);
    TradingPartnerDto maker = partner("Maker", PartnerType.SUPPLIER);
    PartnerLinkDto link =
        linkService.link(
            customer.getId(), linkRequest(maker.getId(), PartnerLinkRole.GARMENT_MAKER, null));
    Tenant owner = tenant;

    Tenant other = newTenant("B");
    TradingPartnerDto otherMaker = partner("Other maker", PartnerType.SUPPLIER);

    assertThatThrownBy(() -> linkService.list(customer.getId()))
        .isInstanceOf(NotFoundException.class);
    assertThatThrownBy(() -> linkService.unlink(customer.getId(), link.id()))
        .isInstanceOf(NotFoundException.class);
    assertThatThrownBy(
            () ->
                linkService.link(
                    customer.getId(),
                    linkRequest(otherMaker.getId(), PartnerLinkRole.GARMENT_MAKER, null)))
        .isInstanceOf(NotFoundException.class);
    assertThat(linkRows(other.getId(), false)).isZero();

    useTenant(owner);
    assertThat(linkService.list(customer.getId())).containsExactly(link);
    assertThatThrownBy(
            () ->
                linkService.link(
                    customer.getId(),
                    linkRequest(otherMaker.getId(), PartnerLinkRole.GARMENT_MAKER, null)))
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  void quickCreateWritesNothingWhileSimilarPartnersExistAndAsksForADecision() {
    TradingPartnerDto customer = partner("Customer", PartnerType.CUSTOMER);
    TradingPartnerDto existing = partner("Delta Garments Ltd", PartnerType.SUPPLIER);
    int partnersBefore = partnerCount(tenant.getId());

    QuickCreatePartnerLinkResponse response =
        linkService.quickCreate(
            customer.getId(),
            quick("Delta Garments", null, "GBR", PartnerLinkRole.GARMENT_MAKER, false));

    assertThat(response.created()).isFalse();
    assertThat(response.partner()).isNull();
    assertThat(response.link()).isNull();
    assertThat(response.matches())
        .extracting(PartnerMatchDto::partnerId, PartnerMatchDto::matchedOn)
        .containsExactly(tuple(existing.getId(), PartnerMatchReason.NAME));
    assertThat(partnerCount(tenant.getId())).isEqualTo(partnersBefore);
    assertThat(linkRows(tenant.getId(), false)).isZero();
  }

  @Test
  void quickCreateWithNoSimilarPartnerCreatesADeliveryPartnerWithItsOrganizationAndLinksIt() {
    TradingPartnerDto customer = partner("Customer", PartnerType.CUSTOMER);

    QuickCreatePartnerLinkResponse response =
        linkService.quickCreate(
            customer.getId(),
            quick("Brand New Dyers " + suffix, null, "gbr", PartnerLinkRole.DYEHOUSE, false));

    assertThat(response.created()).isTrue();
    assertThat(response.matches()).isEmpty();
    TradingPartnerDto created = response.partner();
    assertThat(created.getPartnerType()).isEqualTo(PartnerType.DELIVERY_PARTNER);
    assertThat(created.getOrganizationId()).isNotNull();
    assertThat(created.getCountry()).isEqualTo("GBR");
    assertThat(created.getRelationshipMeta())
        .containsEntry("quick_create_city", "Leicester")
        .containsEntry("quick_create_phone", "+44 116 000 0000")
        .containsEntry("quick_created", true);
    assertThat(response.link().linkedPartnerId()).isEqualTo(created.getId());
    assertThat(response.link().role()).isEqualTo(PartnerLinkRole.DYEHOUSE);
    assertThat(linkService.list(customer.getId())).containsExactly(response.link());
  }

  @Test
  void aTaxIdMatchIsOfferedAndCreateAnywayReusesThePartnerWithoutChangingItsType() {
    TradingPartnerDto customer = partner("Customer", PartnerType.CUSTOMER);
    String taxId = "TAX" + suffix;
    TradingPartnerDto existing =
        partnerService.createPartner(
            CreateTradingPartnerRequest.builder()
                .companyName("Registered Mill " + suffix)
                .taxId(taxId)
                .country("GBR")
                .partnerType(PartnerType.SUPPLIER)
                .build());
    int partnersBefore = partnerCount(tenant.getId());

    QuickCreatePartnerLinkResponse offered =
        linkService.quickCreate(
            customer.getId(),
            quick("A Different Trading Name", taxId, "GBR", PartnerLinkRole.WAREHOUSE, false));

    assertThat(offered.created()).isFalse();
    assertThat(offered.matches())
        .extracting(PartnerMatchDto::partnerId, PartnerMatchDto::matchedOn)
        .containsExactly(tuple(existing.getId(), PartnerMatchReason.TAX_ID));

    QuickCreatePartnerLinkResponse confirmed =
        linkService.quickCreate(
            customer.getId(),
            quick("A Different Trading Name", taxId, "GBR", PartnerLinkRole.WAREHOUSE, true));

    assertThat(confirmed.created()).isTrue();
    assertThat(confirmed.partner().getId()).isEqualTo(existing.getId());
    assertThat(confirmed.partner().getPartnerType()).isEqualTo(PartnerType.SUPPLIER);
    assertThat(partnerCount(tenant.getId())).isEqualTo(partnersBefore);
    assertThat(linkService.list(customer.getId())).hasSize(1);
  }

  @Test
  void quickCreateNeverLinksTheCustomerToItself() {
    String taxId = "SELF" + suffix;
    TradingPartnerDto customer =
        partnerService.createPartner(
            CreateTradingPartnerRequest.builder()
                .companyName("Self Customer " + suffix)
                .taxId(taxId)
                .country("GBR")
                .partnerType(PartnerType.CUSTOMER)
                .build());

    assertThatThrownBy(
            () ->
                linkService.quickCreate(
                    customer.getId(),
                    quick("Self Customer", taxId, "GBR", PartnerLinkRole.OTHER, true)))
        .isInstanceOfSatisfying(
            PlatformDomainException.class,
            ex -> assertThat(ex.getErrorCode()).isEqualTo(PartnerLinkService.CODE_SELF_LINK));
  }

  @Test
  void aDeliveryPartnerNeverShowsUpAsACustomerOrSupplier() {
    TradingPartnerDto customer = partner("Customer", PartnerType.CUSTOMER);
    TradingPartnerDto delivery =
        linkService
            .quickCreate(
                customer.getId(),
                quick("Delivery Only " + suffix, null, "GBR", PartnerLinkRole.PRINTER, false))
            .partner();

    assertThat(partnerService.findCustomers(tenant.getId()))
        .extracting(TradingPartnerDto::getId)
        .contains(customer.getId())
        .doesNotContain(delivery.getId());
    assertThat(partnerService.findSuppliers(tenant.getId()))
        .extracting(TradingPartnerDto::getId)
        .doesNotContain(delivery.getId());
    assertThat(partnerService.findByType(tenant.getId(), PartnerType.CUSTOMER))
        .extracting(TradingPartnerDto::getId)
        .doesNotContain(delivery.getId());
    assertThat(partnerService.findByType(tenant.getId(), PartnerType.DELIVERY_PARTNER))
        .extracting(TradingPartnerDto::getId)
        .containsExactly(delivery.getId());
    assertThat(partnerResolver.findCustomerIdsByNameContains(tenant.getId(), "Delivery Only"))
        .isEmpty();
    assertThat(partnerResolver.resolveOwnershipSnapshot(tenant.getId(), delivery.getId()))
        .hasValueSatisfying(snapshot -> assertThat(snapshot.customer()).isFalse());
  }

  @Test
  void aDeliveryPartnerThatBecomesACustomerKeepsItsRecordLinksAndOrganization() {
    TradingPartnerDto customer = partner("Customer", PartnerType.CUSTOMER);
    String taxId = "DLV" + suffix;
    TradingPartnerDto delivery =
        linkService
            .quickCreate(
                customer.getId(),
                quick("Soon A Customer " + suffix, taxId, "GBR", PartnerLinkRole.DYEHOUSE, false))
            .partner();

    TradingPartnerDto converted =
        partnerService.createPartner(
            CreateTradingPartnerRequest.builder()
                .companyName("Soon A Customer " + suffix)
                .taxId(taxId)
                .country("GBR")
                .partnerType(PartnerType.CUSTOMER)
                .build());

    assertThat(converted.getId()).isEqualTo(delivery.getId());
    assertThat(converted.getPartnerType()).isEqualTo(PartnerType.CUSTOMER);
    assertThat(converted.getOrganizationId()).isEqualTo(delivery.getOrganizationId());
    assertThat(linkService.list(customer.getId()))
        .extracting(PartnerLinkDto::linkedPartnerId)
        .containsExactly(delivery.getId());
    assertThat(partnerService.findCustomers(tenant.getId()))
        .extracting(TradingPartnerDto::getId)
        .contains(delivery.getId());
  }

  // ── fixtures ────────────────────────────────────────────────────────────

  private Tenant newTenant(String label) {
    TenantContext.clear();
    String tenantSuffix =
        UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
    Tenant created =
        Tenant.create("Partner link " + label + " " + tenantSuffix, "PLK-" + tenantSuffix);
    created.activate("test");
    created = tenants.saveAndFlush(created);
    useTenant(created);
    organizations.saveAndFlush(
        Organization.create(
            "Partner link org " + tenantSuffix, "TAX" + tenantSuffix, OrganizationType.SPINNER));
    tenant = created;
    return created;
  }

  private void useTenant(Tenant selected) {
    TenantContext.setCurrentTenantId(selected.getId());
    TenantContext.setCurrentTenantUid(selected.getUid());
    tenant = selected;
  }

  private TradingPartnerDto partner(String name, PartnerType type) {
    return partnerService.createPartner(
        CreateTradingPartnerRequest.builder()
            .companyName(name + " " + suffix)
            .country("GBR")
            .partnerType(type)
            .build());
  }

  private static CreatePartnerLinkRequest linkRequest(
      UUID linkedPartnerId, PartnerLinkRole role, String note) {
    CreatePartnerLinkRequest request = new CreatePartnerLinkRequest();
    request.setLinkedPartnerId(linkedPartnerId);
    request.setRole(role);
    request.setNote(note);
    return request;
  }

  private static QuickCreatePartnerLinkRequest quick(
      String name, String taxId, String country, PartnerLinkRole role, boolean createAnyway) {
    QuickCreatePartnerLinkRequest request = new QuickCreatePartnerLinkRequest();
    request.setCompanyName(name);
    request.setTaxId(taxId);
    request.setCountry(country);
    request.setCity("Leicester");
    request.setPhone("+44 116 000 0000");
    request.setRole(role);
    request.setCreateAnyway(createAnyway);
    return request;
  }

  private int partnerCount(UUID tenantId) {
    entityManager.flush();
    return jdbc.queryForObject(
        "SELECT count(*) FROM common_company.common_trading_partner WHERE tenant_id = ?",
        Integer.class,
        tenantId);
  }

  private int linkRows(UUID tenantId, boolean activeOnly) {
    entityManager.flush();
    return jdbc.queryForObject(
        "SELECT count(*) FROM common_company.partner_link WHERE tenant_id = ?"
            + (activeOnly ? " AND is_active = TRUE" : ""),
        Integer.class,
        tenantId);
  }

  private void assertDatabaseRejects(String sql, Object... args) {
    entityManager.flush();
    jdbc.execute(
        (ConnectionCallback<Void>)
            connection -> {
              Savepoint savepoint = connection.setSavepoint();
              try (PreparedStatement statement = connection.prepareStatement(sql)) {
                for (int i = 0; i < args.length; i++) {
                  statement.setObject(i + 1, args[i]);
                }
                assertThatThrownBy(statement::executeUpdate).isInstanceOf(SQLException.class);
              } finally {
                connection.rollback(savepoint);
              }
              return null;
            });
  }
}
