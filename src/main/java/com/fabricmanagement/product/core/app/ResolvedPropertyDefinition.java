package com.fabricmanagement.product.core.app;

import com.fabricmanagement.product.core.domain.registry.PropertyDefinition;
import com.fabricmanagement.product.core.domain.registry.policy.ConversionPolicy;
import com.fabricmanagement.product.core.domain.registry.policy.RoundingPolicy;
import java.util.Optional;

public record ResolvedPropertyDefinition(
    PropertyDefinition definition,
    Optional<ConversionPolicy> conversionPolicy,
    Optional<RoundingPolicy> roundingPolicy) {}
