package com.fabricmanagement.platform.user.app;

import com.fabricmanagement.common.infrastructure.security.PermissionKey;
import com.fabricmanagement.platform.user.dto.PermissionCatalogueEntryDto;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;

/** Publishes the global vocabulary without reading tenant data or evaluating a user's grants. */
@Service
public class PermissionCatalogueService {
  public List<PermissionCatalogueEntryDto> catalogue() {
    return Arrays.stream(PermissionKey.values())
        .sorted(Comparator.comparing(PermissionKey::key))
        .map(PermissionCatalogueEntryDto::from)
        .toList();
  }
}
