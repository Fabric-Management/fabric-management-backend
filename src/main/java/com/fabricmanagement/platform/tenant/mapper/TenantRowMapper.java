package com.fabricmanagement.platform.tenant.mapper;

import com.fabricmanagement.platform.tenant.domain.TenantStatus;
import com.fabricmanagement.platform.tenant.domain.TenantType;
import com.fabricmanagement.platform.tenant.dto.TenantDto;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;

/** RowMapper for mapping common_tenant rows to TenantDto. */
public class TenantRowMapper implements RowMapper<TenantDto> {

  public static final TenantRowMapper INSTANCE = new TenantRowMapper();

  private TenantRowMapper() {
    // Hide constructor, use INSTANCE
  }

  @Override
  public TenantDto mapRow(ResultSet rs, int rowNum) throws SQLException {
    return TenantDto.builder()
        .id(rs.getObject("id", UUID.class))
        .uid(rs.getString("uid"))
        .slug(rs.getString("slug"))
        .name(rs.getString("name"))
        .status(
            TenantStatus.valueOf(
                Objects.requireNonNull(rs.getString("status"), "status column is NULL")))
        .type(
            TenantType.valueOf(Objects.requireNonNull(rs.getString("type"), "type column is NULL")))
        .billingEmail(rs.getString("billing_email"))
        .isActive(rs.getBoolean("is_active"))
        .build();
  }
}
