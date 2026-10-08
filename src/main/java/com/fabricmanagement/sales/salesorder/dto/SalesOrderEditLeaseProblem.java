package com.fabricmanagement.sales.salesorder.dto;

import com.fabricmanagement.common.infrastructure.web.exception.ApiProblemDetail;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * The 409 of a lease that somebody else holds (CEDIT-07 §4): code {@code EDIT_LEASE_UNAVAILABLE}
 * when acquiring, {@code EDIT_LEASE_HELD} when another writer (a product correction, a quantity
 * acceptance, the requested-date section, the legacy update) would change a leased key. {@code
 * holders} names who is in the way, without any token. Other 409 codes carry only the standard
 * fields. Documentation type: the body is written from the domain failure's details.
 */
@Getter
@Setter
@Schema(name = "SalesOrderEditLeaseProblem")
public class SalesOrderEditLeaseProblem extends ApiProblemDetail {

  @JsonInclude(JsonInclude.Include.NON_NULL)
  @ArraySchema(
      arraySchema =
          @Schema(
              description =
                  "The leases in the way (EDIT_LEASE_UNAVAILABLE, EDIT_LEASE_HELD), in key order"))
  private List<SalesOrderEditLeaseDtos.Holder> holders;

  protected SalesOrderEditLeaseProblem() {
    super();
  }
}
