package com.fabricmanagement.production.masterdata.color.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.production.masterdata.color.domain.Color;
import com.fabricmanagement.production.masterdata.color.domain.ColorCardSpec;
import com.fabricmanagement.production.masterdata.color.domain.ColorFamily;
import com.fabricmanagement.production.masterdata.color.domain.ColorStandardStatus;
import com.fabricmanagement.production.masterdata.color.domain.ColorType;
import com.fabricmanagement.production.masterdata.color.domain.PantoneSystem;
import java.sql.DriverManager;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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
class ColorPersistenceIT {

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

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void filtersAreCombinableLiteralAndPaginated() {
    UUID tenantId = UUID.fromString("cccccccc-cccc-4ccc-8ccc-ccccccccccc3");
    TenantContext.setCurrentTenantId(tenantId);

    Color literal =
        colorService.create(
            ColorCardSpec.builder()
                .code("LIT_A")
                .name("Literal Blue")
                .colorType(ColorType.YARN_DYED)
                .colorFamily(ColorFamily.BLUE)
                .pantoneCode("19-4024")
                .pantoneSystem(PantoneSystem.TCX)
                .build());
    colorService.approve(literal.getId());
    colorService.create(
        ColorCardSpec.builder()
            .code("LITXA")
            .name("Wildcard Lookalike")
            .colorType(ColorType.DYED)
            .colorFamily(ColorFamily.RED)
            .build());
    colorService.create("PCT%ONE", "Literal percent", null);
    colorService.create("PCTXONE", "Percent lookalike", null);
    colorService.create("SLASH\\ONE", "Literal slash", null);
    colorService.create("SLASHXONE", "Slash lookalike", null);
    Color inactive = colorService.create("ZZZ-INACTIVE", "Inactive", "#ABCDEF");
    colorService.deactivate(inactive.getId());

    PageRequest defaultPage = PageRequest.of(0, 20, Sort.by("code"));

    assertThat(colorService.list("lit_a", null, null, null, false, defaultPage).getContent())
        .extracting(Color::getCode)
        .containsExactly("LIT_A");
    assertThat(colorService.list("pct%one", null, null, null, false, defaultPage).getContent())
        .extracting(Color::getCode)
        .containsExactly("PCT%ONE");
    assertThat(colorService.list("slash\\one", null, null, null, false, defaultPage).getContent())
        .extracting(Color::getCode)
        .containsExactly("SLASH\\ONE");
    assertThat(
            colorService
                .list(null, ColorType.YARN_DYED, null, null, false, defaultPage)
                .getContent())
        .extracting(Color::getCode)
        .containsExactly("LIT_A");
    assertThat(
            colorService.list(null, null, ColorFamily.BLUE, null, false, defaultPage).getContent())
        .extracting(Color::getCode)
        .containsExactly("LIT_A");
    assertThat(
            colorService
                .list(null, null, null, ColorStandardStatus.APPROVED, false, defaultPage)
                .getContent())
        .extracting(Color::getCode)
        .containsExactly("LIT_A");
    assertThat(colorService.list(null, null, null, null, false, defaultPage).getContent())
        .extracting(Color::getCode)
        .doesNotContain("ZZZ-INACTIVE");
    assertThat(colorService.list(null, null, null, null, true, defaultPage).getContent())
        .extracting(Color::getCode)
        .contains("ZZZ-INACTIVE");
    assertThat(
            colorService
                .list(
                    "literal",
                    ColorType.YARN_DYED,
                    ColorFamily.BLUE,
                    ColorStandardStatus.APPROVED,
                    false,
                    defaultPage)
                .getContent())
        .extracting(Color::getCode)
        .containsExactly("LIT_A");

    UUID paginationTenant = UUID.fromString("dddddddd-dddd-4ddd-8ddd-ddddddddddd4");
    TenantContext.setCurrentTenantId(paginationTenant);
    colorService.create("PAGE-C", "C", null);
    colorService.create("PAGE-A", "A", null);
    colorService.create("PAGE-B", "B", null);

    Page<Color> page =
        colorService.list(null, null, null, null, false, PageRequest.of(0, 2, Sort.by("code")));

    assertThat(page.getSize()).isEqualTo(2);
    assertThat(page.getTotalElements()).isEqualTo(3);
    assertThat(page.getContent()).extracting(Color::getCode).containsExactly("PAGE-A", "PAGE-B");
  }

  @Test
  void tenantBHasNoReadOrMutationAccessToTenantAColor() {
    TenantContext.setCurrentTenantId(TENANT_A);
    Color owned = colorService.create("TENANT-A-COLOR", "Tenant A", "#123456");

    TenantContext.setCurrentTenantId(TENANT_B);
    Page<Color> list =
        colorService.list(null, null, null, null, true, PageRequest.of(0, 20, Sort.by("code")));

    assertThat(list.getContent()).extracting(Color::getId).doesNotContain(owned.getId());
    assertThatThrownBy(() -> colorService.findById(owned.getId()))
        .isInstanceOf(NotFoundException.class);
    assertThatThrownBy(
            () -> colorService.update(owned.getId(), ColorCardSpec.basic("HACKED", "Hacked", null)))
        .isInstanceOf(NotFoundException.class);
    assertThatThrownBy(() -> colorService.deactivate(owned.getId()))
        .isInstanceOf(NotFoundException.class);
    assertThatThrownBy(() -> colorService.approve(owned.getId()))
        .isInstanceOf(NotFoundException.class);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("nonCanonicalRawColorRows")
  void databaseRejectsNonCanonicalValuesWhenDomainIsBypassed(
      String scenario,
      String code,
      String name,
      String pantoneCode,
      String colorHex,
      String expectedConstraint) {
    assertThatThrownBy(() -> insertRawColor(code, name, pantoneCode, colorHex))
        .hasMessageContaining(expectedConstraint);
  }

  private static Stream<Arguments> nonCanonicalRawColorRows() {
    return Stream.of(
        Arguments.of(
            "lowercase code", "lower-case", "Raw writer", null, null, "chk_color_code_canonical"),
        Arguments.of(
            "untrimmed code", " PADDED ", "Raw writer", null, null, "chk_color_code_canonical"),
        Arguments.of("blank code", "", "Raw writer", null, null, "chk_color_code_not_blank"),
        Arguments.of(
            "untrimmed name", "VALID-CODE", " Raw writer ", null, null, "chk_color_name_canonical"),
        Arguments.of("blank name", "VALID-CODE", "", null, null, "chk_color_name_not_blank"),
        Arguments.of(
            "lowercase Pantone code",
            "VALID-CODE",
            "Raw writer",
            "19-4024 tcx",
            null,
            "chk_color_pantone_code_canonical"),
        Arguments.of(
            "untrimmed Pantone code",
            "VALID-CODE",
            "Raw writer",
            " 19-4024 ",
            null,
            "chk_color_pantone_code_canonical"),
        Arguments.of(
            "blank Pantone code",
            "VALID-CODE",
            "Raw writer",
            "",
            null,
            "chk_color_pantone_code_not_blank"),
        Arguments.of(
            "lowercase color hex",
            "VALID-CODE",
            "Raw writer",
            null,
            "#abcdef",
            "chk_color_hex_uppercase"));
  }

  private void insertRawColor(String code, String name, String pantoneCode, String colorHex)
      throws Exception {
    try (var connection =
            DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        var statement =
            connection.prepareStatement(
                "INSERT INTO production.color "
                    + "(id, tenant_id, created_at, updated_at, is_active, version, "
                    + "code, name, pantone_code, color_hex, color_type, color_family, "
                    + "standard_status) "
                    + "VALUES (?, ?, now(), now(), true, 0, ?, ?, ?, ?, 'DYED', "
                    + "'UNDEFINED', 'DRAFT')")) {
      statement.setObject(1, UUID.randomUUID());
      statement.setObject(2, UUID.randomUUID());
      statement.setString(3, code);
      statement.setString(4, name);
      statement.setString(5, pantoneCode);
      statement.setString(6, colorHex);
      statement.executeUpdate();
    }
  }
}
