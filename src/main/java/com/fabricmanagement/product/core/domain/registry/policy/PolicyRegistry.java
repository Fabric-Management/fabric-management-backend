package com.fabricmanagement.product.core.domain.registry.policy;

import com.fabricmanagement.product.core.domain.registry.PropertyRegistryException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class PolicyRegistry {

  private final Map<String, ConversionPolicy> conversionPolicies;
  private final Map<String, RoundingPolicy> roundingPolicies;

  public PolicyRegistry(
      List<ConversionPolicy> conversionPolicies, List<RoundingPolicy> roundingPolicies) {
    this.conversionPolicies =
        uniqueByName(conversionPolicies, ConversionPolicy::name, "conversion");
    this.roundingPolicies = uniqueByName(roundingPolicies, RoundingPolicy::name, "rounding");
  }

  public Optional<ConversionPolicy> conversion(String name) {
    return Optional.ofNullable(name).map(conversionPolicies::get);
  }

  public Optional<RoundingPolicy> rounding(String name) {
    return Optional.ofNullable(name).map(roundingPolicies::get);
  }

  private static <T> Map<String, T> uniqueByName(
      List<T> policies, Function<T, String> name, String kind) {
    try {
      return policies.stream().collect(Collectors.toUnmodifiableMap(name, Function.identity()));
    } catch (IllegalStateException duplicate) {
      throw new PropertyRegistryException("Duplicate " + kind + " policy name", duplicate);
    }
  }
}
