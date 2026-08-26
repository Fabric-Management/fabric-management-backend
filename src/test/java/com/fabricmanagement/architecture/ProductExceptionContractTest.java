package com.fabricmanagement.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.fabricmanagement.product.color.domain.exception.ColorDomainException;
import com.fabricmanagement.product.common.exception.ForbiddenOperationException;
import com.fabricmanagement.product.common.exception.ProductDomainException;
import com.fabricmanagement.product.fiber.domain.exception.FiberDomainException;
import com.fabricmanagement.product.qualitygrade.domain.exception.QualityGradeDomainException;
import com.fabricmanagement.product.recipe.domain.exception.RecipeDomainException;
import com.fabricmanagement.production.common.exception.ProductionDomainException;
import com.fabricmanagement.production.core.batch.domain.exception.BatchDomainException;
import com.fabricmanagement.production.core.stockunit.domain.exception.StockUnitDomainException;
import com.fabricmanagement.production.core.workorder.domain.exception.WorkOrderDomainException;
import org.junit.jupiter.api.Test;

class ProductExceptionContractTest {

  @Test
  void movedDefaultExceptionFamiliesUseProductRuleViolation() {
    assertDefaultProductContract(new ProductDomainException("product"), "product");
    assertDefaultProductContract(new FiberDomainException("fiber"), "fiber");
    assertDefaultProductContract(new RecipeDomainException("recipe"), "recipe");
    assertDefaultProductContract(new QualityGradeDomainException("grade"), "grade");
  }

  @Test
  void movedExplicitOverridesAndDetailsRemainStable() {
    FiberDomainException fiber =
        new FiberDomainException("missing", "FIBER_NOT_FOUND", 404, new Object[] {"fiber-id"});
    assertThat(fiber.getMessage()).isEqualTo("missing");
    assertThat(fiber.getErrorCode()).isEqualTo("FIBER_NOT_FOUND");
    assertThat(fiber.getHttpStatus()).isEqualTo(404);
    assertThat(fiber.getArgs()).containsExactly("fiber-id");
    assertThat(fiber.getDetails()).isEmpty();

    ColorDomainException color = ColorDomainException.invalid("invalid colour");
    assertThat(color.getMessage()).isEqualTo("invalid colour");
    assertThat(color.getErrorCode()).isEqualTo("PRODUCTION_COLOR_INVALID");
    assertThat(color.getHttpStatus()).isEqualTo(422);

    ForbiddenOperationException forbidden = new ForbiddenOperationException("read-only");
    assertThat(forbidden.getMessage()).isEqualTo("read-only");
    assertThat(forbidden.getErrorCode()).isEqualTo("FORBIDDEN_OPERATION");
    assertThat(forbidden.getHttpStatus()).isEqualTo(403);
  }

  @Test
  void productionExceptionFamiliesKeepTheirExistingCodes() {
    assertProductionContract(new ProductionDomainException("production"));
    assertProductionContract(new BatchDomainException("batch"));
    assertProductionContract(new WorkOrderDomainException("work order"));

    StockUnitDomainException stockUnit = new StockUnitDomainException("stock unit");
    assertThat(stockUnit.getMessage()).isEqualTo("stock unit");
    assertThat(stockUnit.getErrorCode()).isEqualTo("STOCK_UNIT_RULE_VIOLATION");
    assertThat(stockUnit.getHttpStatus()).isEqualTo(400);
  }

  private static void assertDefaultProductContract(
      ProductDomainException exception, String expectedMessage) {
    assertThat(exception.getMessage()).isEqualTo(expectedMessage);
    assertThat(exception.getErrorCode()).isEqualTo("PRODUCT_RULE_VIOLATION");
    assertThat(exception.getHttpStatus()).isEqualTo(400);
    assertThat(exception.getArgs()).isEmpty();
    assertThat(exception.getDetails()).isEmpty();
  }

  private static void assertProductionContract(ProductionDomainException exception) {
    assertThat(exception.getErrorCode()).isEqualTo("PRODUCTION_RULE_VIOLATION");
    assertThat(exception.getHttpStatus()).isEqualTo(400);
  }
}
