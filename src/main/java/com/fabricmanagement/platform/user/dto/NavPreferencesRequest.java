package com.fabricmanagement.platform.user.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for PATCH /api/common/users/{id}/nav-preferences.
 *
 * <p>Contract: {@code docs/NAVPREF_API_CONTRACT.md}. Both fields are optional; only send changed
 * fields.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NavPreferencesRequest {

  private List<String> sortOrder;
  private List<String> hiddenItemIds;
}
