package com.fabricmanagement.product.core.app.config;

import com.fabricmanagement.product.core.domain.registry.PropertyDefinitionValidator;
import com.fabricmanagement.product.core.domain.registry.policy.ConversionPolicy;
import com.fabricmanagement.product.core.domain.registry.policy.LinearDensityV1;
import com.fabricmanagement.product.core.domain.registry.policy.PolicyRegistry;
import com.fabricmanagement.product.core.domain.registry.policy.RoundingPolicy;
import com.fabricmanagement.product.core.domain.registry.policy.Tex2dp;
import com.fabricmanagement.product.core.domain.registry.policy.Tpm2dp;
import com.fabricmanagement.product.core.domain.registry.policy.TwistV1;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Spring wiring for the framework-agnostic Property Registry domain services and policies. */
@Configuration
public class PropertyRegistryConfiguration {

  @Bean
  ConversionPolicy twistV1() {
    return TwistV1.INSTANCE;
  }

  @Bean
  ConversionPolicy linearDensityV1() {
    return LinearDensityV1.INSTANCE;
  }

  @Bean
  RoundingPolicy tex2dp() {
    return Tex2dp.INSTANCE;
  }

  @Bean
  RoundingPolicy tpm2dp() {
    return Tpm2dp.INSTANCE;
  }

  @Bean
  PolicyRegistry policyRegistry(
      List<ConversionPolicy> conversionPolicies, List<RoundingPolicy> roundingPolicies) {
    return new PolicyRegistry(conversionPolicies, roundingPolicies);
  }

  @Bean
  PropertyDefinitionValidator propertyDefinitionValidator(PolicyRegistry policyRegistry) {
    return new PropertyDefinitionValidator(policyRegistry);
  }
}
