package com.fabricmanagement.platform.user.dto;

import com.fabricmanagement.platform.tenant.dto.TenantDto;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.springframework.beans.BeanUtils;

public class CurrentUserResponse extends UserDto {

  private CurrentTenantContextDto tenant;

  @Schema(
      requiredMode = Schema.RequiredMode.REQUIRED,
      description =
          "Permission resources the organisation's enabled packages do not currently open. A user"
              + " (administrators included) is refused these regardless of role; the API answers"
              + " PACKAGE_NOT_ENABLED. Empty when every resource is open.")
  private List<String> closedResources = List.of();

  public CurrentTenantContextDto getTenant() {
    return tenant;
  }

  public void setTenant(CurrentTenantContextDto tenant) {
    this.tenant = tenant;
  }

  public List<String> getClosedResources() {
    return closedResources;
  }

  public void setClosedResources(List<String> closedResources) {
    this.closedResources = closedResources == null ? List.of() : List.copyOf(closedResources);
  }

  public static CurrentUserResponse from(UserDto user, TenantDto tenant) {
    CurrentUserResponse response = new CurrentUserResponse();
    BeanUtils.copyProperties(user, response);
    response.setTenant(CurrentTenantContextDto.from(tenant));
    return response;
  }
}
