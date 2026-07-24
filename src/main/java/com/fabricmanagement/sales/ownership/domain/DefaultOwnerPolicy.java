package com.fabricmanagement.sales.ownership.domain;

/** Resolves a quote's commercial owner from stable customer ownership facts. */
public interface DefaultOwnerPolicy {
  OwnerResolution resolve(OwnerResolutionContext context);
}
