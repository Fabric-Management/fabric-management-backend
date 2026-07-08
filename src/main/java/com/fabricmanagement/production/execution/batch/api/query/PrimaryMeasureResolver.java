package com.fabricmanagement.production.execution.batch.api.query;

import com.fabricmanagement.production.masterdata.product.domain.ProductType;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;

/** Node-map classification for the measure that drives quote quantity derivation. */
@Component
class PrimaryMeasureResolver {

  private static final String ORIGIN_PURCHASE = "PURCHASE";
  private static final String ORIGIN_SUBCONTRACT = "SUBCONTRACT";
  private static final String ORIGIN_ADJUSTMENT = "ADJUSTMENT";
  private static final String ORIGIN_RETURN = "RETURN";
  private static final String ORIGIN_INITIAL_STOCK = "INITIAL_STOCK";
  private static final String INTERNAL_PRODUCTION = "INTERNAL_PRODUCTION";

  private static final List<PrimaryMeasureNode> NODE_MAP =
      List.of(
          node("SPINNING", ProductType.YARN, PrimaryMeasure.WEIGHT),
          node("WEAVING", ProductType.FABRIC, PrimaryMeasure.LENGTH),
          node("KNITTING", ProductType.FABRIC, PrimaryMeasure.LENGTH),
          node("DYEING", ProductType.FABRIC, PrimaryMeasure.LENGTH),
          node("DYEING", ProductType.YARN, PrimaryMeasure.WEIGHT),
          node("FINISHING", ProductType.FABRIC, PrimaryMeasure.LENGTH),
          node("GENERIC", ProductType.FIBER, PrimaryMeasure.WEIGHT),
          node("GENERIC", ProductType.YARN, PrimaryMeasure.WEIGHT),
          node("GENERIC", ProductType.FABRIC, PrimaryMeasure.LENGTH),
          node(INTERNAL_PRODUCTION, ProductType.FIBER, PrimaryMeasure.WEIGHT),
          node(INTERNAL_PRODUCTION, ProductType.YARN, PrimaryMeasure.WEIGHT),
          node(INTERNAL_PRODUCTION, ProductType.FABRIC, PrimaryMeasure.LENGTH),
          originNode(ORIGIN_PURCHASE, ProductType.FIBER, PrimaryMeasure.WEIGHT),
          originNode(ORIGIN_PURCHASE, ProductType.YARN, PrimaryMeasure.WEIGHT),
          originNode(ORIGIN_PURCHASE, ProductType.FABRIC, PrimaryMeasure.LENGTH),
          originNode(ORIGIN_SUBCONTRACT, ProductType.FIBER, PrimaryMeasure.WEIGHT),
          originNode(ORIGIN_SUBCONTRACT, ProductType.YARN, PrimaryMeasure.WEIGHT),
          originNode(ORIGIN_SUBCONTRACT, ProductType.FABRIC, PrimaryMeasure.LENGTH),
          originNode(ORIGIN_ADJUSTMENT, ProductType.FIBER, PrimaryMeasure.WEIGHT),
          originNode(ORIGIN_ADJUSTMENT, ProductType.YARN, PrimaryMeasure.WEIGHT),
          originNode(ORIGIN_ADJUSTMENT, ProductType.FABRIC, PrimaryMeasure.LENGTH),
          originNode(ORIGIN_RETURN, ProductType.FIBER, PrimaryMeasure.WEIGHT),
          originNode(ORIGIN_RETURN, ProductType.YARN, PrimaryMeasure.WEIGHT),
          originNode(ORIGIN_RETURN, ProductType.FABRIC, PrimaryMeasure.LENGTH),
          originNode(ORIGIN_INITIAL_STOCK, ProductType.FIBER, PrimaryMeasure.WEIGHT),
          originNode(ORIGIN_INITIAL_STOCK, ProductType.YARN, PrimaryMeasure.WEIGHT),
          originNode(ORIGIN_INITIAL_STOCK, ProductType.FABRIC, PrimaryMeasure.LENGTH));

  PrimaryMeasure resolve(String processType, ProductType outputProductType) {
    return NODE_MAP.stream()
        .filter(node -> node.matches(processType, outputProductType))
        .findFirst()
        .map(PrimaryMeasureNode::primaryMeasure)
        .orElse(PrimaryMeasure.WEIGHT);
  }

  private static PrimaryMeasureNode originNode(
      String processType, ProductType outputProductType, PrimaryMeasure primaryMeasure) {
    return node(processType, outputProductType, primaryMeasure);
  }

  private static PrimaryMeasureNode node(
      String processType, ProductType outputProductType, PrimaryMeasure primaryMeasure) {
    return new PrimaryMeasureNode(processType, outputProductType, primaryMeasure);
  }

  private record PrimaryMeasureNode(
      String processType, ProductType outputProductType, PrimaryMeasure primaryMeasure) {

    boolean matches(String candidateProcessType, ProductType candidateOutputProductType) {
      return Objects.equals(processType, candidateProcessType)
          && outputProductType == candidateOutputProductType;
    }
  }
}
