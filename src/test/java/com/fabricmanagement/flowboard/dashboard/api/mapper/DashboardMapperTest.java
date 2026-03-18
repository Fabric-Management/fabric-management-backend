package com.fabricmanagement.flowboard.dashboard.api.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.fabricmanagement.flowboard.dashboard.api.dto.response.DashboardConfigDto;
import com.fabricmanagement.flowboard.dashboard.api.dto.response.DashboardWidgetDto;
import com.fabricmanagement.flowboard.dashboard.domain.DashboardConfig;
import com.fabricmanagement.flowboard.dashboard.domain.DashboardWidget;
import com.fabricmanagement.flowboard.dashboard.domain.WidgetType;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("DashboardMapper")
class DashboardMapperTest {

  private final UUID tenantId = UUID.randomUUID();
  private final UUID userId = UUID.randomUUID();
  private final UUID dashboardId = UUID.randomUUID();

  @Nested
  @DisplayName("toDto(DashboardConfig)")
  class ConfigMapping {

    @Test
    @DisplayName("Null entity → null DTO döner")
    void nullEntity_returnsNull() {
      assertThat(DashboardMapper.toDto((DashboardConfig) null)).isNull();
    }

    @Test
    @DisplayName("Tüm alanlar doğru eşlenir")
    void allFieldsMapped() {
      var config = new DashboardConfig(tenantId, userId, "Test Dashboard", true, "{\"cols\": 3}");
      DashboardConfigDto dto = DashboardMapper.toDto(config);

      assertThat(dto).isNotNull();
      assertThat(dto.userId()).isEqualTo(userId);
      assertThat(dto.name()).isEqualTo("Test Dashboard");
      assertThat(dto.isDefault()).isTrue();
      assertThat(dto.layoutJsonb()).isEqualTo("{\"cols\": 3}");
    }

    @Test
    @DisplayName("Null layoutJsonb korunur")
    void nullLayout_preserved() {
      var config = new DashboardConfig(tenantId, userId, "Boş", false, null);
      DashboardConfigDto dto = DashboardMapper.toDto(config);

      assertThat(dto.layoutJsonb()).isNull();
      assertThat(dto.isDefault()).isFalse();
    }
  }

  @Nested
  @DisplayName("toDto(DashboardWidget)")
  class WidgetMapping {

    @Test
    @DisplayName("Null entity → null DTO döner")
    void nullEntity_returnsNull() {
      assertThat(DashboardMapper.toDto((DashboardWidget) null)).isNull();
    }

    @Test
    @DisplayName("Tüm widget alanları doğru eşlenir")
    void allFieldsMapped() {
      var config = new DashboardConfig(tenantId, userId, "Test", true, "{}");
      org.springframework.test.util.ReflectionTestUtils.setField(config, "id", dashboardId);

      var widget = new DashboardWidget(tenantId, config, WidgetType.TASK_COUNT, "Özet", "{}", 1);
      DashboardWidgetDto dto = DashboardMapper.toDto(widget);

      assertThat(dto).isNotNull();
      assertThat(dto.dashboardId()).isEqualTo(dashboardId);
      assertThat(dto.widgetType()).isEqualTo(WidgetType.TASK_COUNT);
      assertThat(dto.title()).isEqualTo("Özet");
      assertThat(dto.configJsonb()).isEqualTo("{}");
      assertThat(dto.displayOrder()).isEqualTo(1);
    }
  }
}
