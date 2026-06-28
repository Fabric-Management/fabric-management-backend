package com.fabricmanagement.platform.user.dto;

import com.fabricmanagement.platform.tenant.dto.TenantDto;
import org.springframework.beans.BeanUtils;

public class CurrentUserResponse extends UserDto {

  private CurrentTenantContextDto tenant;

  public CurrentTenantContextDto getTenant() {
    return tenant;
  }

  public void setTenant(CurrentTenantContextDto tenant) {
    this.tenant = tenant;
  }

  public static CurrentUserResponse from(UserDto user, TenantDto tenant) {
    CurrentUserResponse response = new CurrentUserResponse();
    BeanUtils.copyProperties(user, response);
    response.setTenant(CurrentTenantContextDto.from(tenant));
    return response;
  }
}
