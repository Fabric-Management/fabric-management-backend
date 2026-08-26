package com.fabricmanagement.product.core.domain.registry.policy;

import java.util.Set;

public final class ResolverPolicyNames {

  public static final String YARN_LOT_NOMINAL_V1 = "yarnLotNominal-v1";
  public static final String COMMERCIAL_CONFORMANCE_TOLERANCE = "commercialConformanceTolerance";
  public static final String QUALITY_ACCEPTANCE_PROFILE = "qualityAcceptanceProfile";

  private static final Set<String> KNOWN_NAMES =
      Set.of(YARN_LOT_NOMINAL_V1, COMMERCIAL_CONFORMANCE_TOLERANCE, QUALITY_ACCEPTANCE_PROFILE);

  private ResolverPolicyNames() {}

  public static boolean isKnown(String name) {
    return name == null || KNOWN_NAMES.contains(name);
  }
}
