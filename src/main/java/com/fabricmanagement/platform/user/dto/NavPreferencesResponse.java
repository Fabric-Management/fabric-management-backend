package com.fabricmanagement.platform.user.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for GET /api/common/users/{id}/nav-preferences.
 *
 * <p>Contract: {@code docs/NAVPREF_API_CONTRACT.md}. Both arrays are always present (empty if
 * none).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NavPreferencesResponse {

  private List<String> sortOrder;
  private List<String> hiddenItemIds;
}
