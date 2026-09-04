package com.fabricmanagement.product.yarn.app.port;

import java.util.UUID;

/** Consumer-owned port for discovering legacy yarn designation evidence in bulk. */
public interface LegacyYarnDesignationSource {

  LegacyDesignationDiscovery discover(UUID tenantId);
}
