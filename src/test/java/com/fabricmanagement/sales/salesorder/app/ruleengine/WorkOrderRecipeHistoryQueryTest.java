package com.fabricmanagement.sales.salesorder.app.ruleengine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

@ExtendWith(MockitoExtension.class)
class WorkOrderRecipeHistoryQueryTest {

  @Mock private NamedParameterJdbcTemplate jdbc;

  @Captor private ArgumentCaptor<MapSqlParameterSource> paramCaptor;

  private WorkOrderRecipeHistoryQuery historyQuery;

  private final UUID tenantId = UUID.randomUUID();
  private final UUID productId = UUID.randomUUID();
  private final UUID partnerId = UUID.randomUUID();
  private final UUID recipeId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    historyQuery = new WorkOrderRecipeHistoryQuery(jdbc);
  }

  @Test
  @SuppressWarnings("unchecked")
  void findDefaultRecipeForProduct_shouldBuildCorrectParams() {
    when(jdbc.query(anyString(), any(MapSqlParameterSource.class), any(ResultSetExtractor.class)))
        .thenReturn(Optional.of(recipeId));

    Optional<UUID> result =
        historyQuery.findDefaultRecipeForProduct(tenantId, productId, "GOTS", "TR");

    assertThat(result).contains(recipeId);
    verify(jdbc).query(anyString(), paramCaptor.capture(), any(ResultSetExtractor.class));

    MapSqlParameterSource params = paramCaptor.getValue();
    assertThat(params.getValue("tenantId")).isEqualTo(tenantId);
    assertThat(params.getValue("productId")).isEqualTo(productId);
    assertThat(params.getValue("certification")).isEqualTo("GOTS");
    assertThat(params.getValue("origin")).isEqualTo("TR");
  }

  @Test
  @SuppressWarnings("unchecked")
  void findMostRecentRecipeForCustomerAndProduct_shouldBuildCorrectParams() {
    when(jdbc.query(anyString(), any(MapSqlParameterSource.class), any(ResultSetExtractor.class)))
        .thenReturn(Optional.of(recipeId));

    Optional<UUID> result =
        historyQuery.findMostRecentRecipeForCustomerAndProduct(
            tenantId, partnerId, productId, null, null);

    assertThat(result).contains(recipeId);
    verify(jdbc).query(anyString(), paramCaptor.capture(), any(ResultSetExtractor.class));

    MapSqlParameterSource params = paramCaptor.getValue();
    assertThat(params.getValue("tenantId")).isEqualTo(tenantId);
    assertThat(params.getValue("tradingPartnerId")).isEqualTo(partnerId);
    assertThat(params.getValue("productId")).isEqualTo(productId);
    assertThat(params.getValue("certification")).isNull();
    assertThat(params.getValue("origin")).isNull();
  }

  @Test
  @SuppressWarnings("unchecked")
  void findMostUsedRecipeForProduct_shouldBuildCorrectParams() {
    when(jdbc.query(anyString(), any(MapSqlParameterSource.class), any(ResultSetExtractor.class)))
        .thenReturn(Optional.empty());

    Optional<UUID> result =
        historyQuery.findMostUsedRecipeForProduct(tenantId, productId, "OEKO-TEX", "EG");

    assertThat(result).isEmpty();
    verify(jdbc).query(anyString(), paramCaptor.capture(), any(ResultSetExtractor.class));

    MapSqlParameterSource params = paramCaptor.getValue();
    assertThat(params.getValue("tenantId")).isEqualTo(tenantId);
    assertThat(params.getValue("productId")).isEqualTo(productId);
    assertThat(params.getValue("certification")).isEqualTo("OEKO-TEX");
    assertThat(params.getValue("origin")).isEqualTo("EG");
  }
}
