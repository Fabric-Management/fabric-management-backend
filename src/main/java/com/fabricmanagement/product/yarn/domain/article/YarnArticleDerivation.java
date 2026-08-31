package com.fabricmanagement.product.yarn.domain.article;

import com.fabricmanagement.product.core.domain.registry.policy.LinearDensityV1;
import com.fabricmanagement.product.yarn.domain.vocabulary.CountBasis;
import com.fabricmanagement.product.yarn.domain.vocabulary.CountSystem;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

final class YarnArticleDerivation {

  private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

  private YarnArticleDerivation() {}

  static BigDecimal componentTex(CountSystem system, BigDecimal value) {
    if (system == null || value == null) {
      return null;
    }
    return scaleTex(LinearDensityV1.INSTANCE.toCanonical(value, system.unitCode()));
  }

  static BigDecimal resultantTex(
      CountSystem system,
      BigDecimal value,
      CountBasis basis,
      Integer foldCount,
      List<YarnArticleStructureComponent> components,
      BigDecimal contractionPercent) {
    List<YarnArticleStructureComponent> strands =
        components.stream()
            .filter(component -> component.getKind() == ComponentKind.STRAND)
            .toList();

    BigDecimal base;
    if (!strands.isEmpty()) {
      if (strands.stream()
          .anyMatch(component -> component.getComponentLinearDensityTex() == null)) {
        return null;
      }
      base =
          strands.stream()
              .map(YarnArticleStructureComponent::getComponentLinearDensityTex)
              .reduce(BigDecimal.ZERO, BigDecimal::add);
    } else {
      if (system == null || value == null || basis == null) {
        return null;
      }
      base = LinearDensityV1.INSTANCE.toCanonical(value, system.unitCode());
      if (basis == CountBasis.COMPONENT) {
        if (foldCount == null) {
          return null;
        }
        base = base.multiply(BigDecimal.valueOf(foldCount));
      }
    }

    if (contractionPercent != null && contractionPercent.signum() != 0) {
      BigDecimal denominator =
          BigDecimal.ONE.subtract(contractionPercent.divide(ONE_HUNDRED, 8, RoundingMode.HALF_UP));
      base = base.divide(denominator, 2, RoundingMode.HALF_UP);
    }
    return scaleTex(base);
  }

  static String designation(
      CountSystem system,
      BigDecimal originalValue,
      Integer foldCount,
      CountBasis basis,
      Integer filamentCount,
      BigDecimal resultantTex) {
    if (system == null || originalValue == null) {
      return resultantTex == null ? null : "tex " + plain(resultantTex);
    }
    String token =
        switch (system) {
          case NE -> "Ne";
          case NM -> "Nm";
          case TEX -> "tex";
          case DTEX -> "dtex";
          case DENIER -> "den";
        };
    StringBuilder value = new StringBuilder(token).append(' ').append(plain(originalValue));
    if (foldCount != null && foldCount > 1) {
      value.append('/').append(foldCount);
    }
    if (basis == CountBasis.RESULTANT) {
      value.append('R');
    }
    if (filamentCount != null) {
      value.append(" f").append(filamentCount);
    }
    return value.toString();
  }

  static BigDecimal scaleTex(BigDecimal value) {
    return value == null ? null : value.setScale(2, RoundingMode.HALF_UP);
  }

  private static String plain(BigDecimal value) {
    return value.stripTrailingZeros().toPlainString();
  }
}
