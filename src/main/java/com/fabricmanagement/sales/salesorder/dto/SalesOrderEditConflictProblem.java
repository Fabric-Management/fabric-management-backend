package com.fabricmanagement.sales.salesorder.dto;

import com.fabricmanagement.common.infrastructure.web.exception.ApiProblemDetail;
import io.swagger.v3.oas.annotations.media.Schema;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * The 409 answer of a safe-edit save that saved nothing (CEDIT-02 §5.6): code {@code EDIT_CONFLICT}
 * or {@code EDIT_BASE_EXPIRED}, the conflicts and the new base they are resolved against. Other 409
 * codes of the endpoint carry only the standard problem fields.
 */
@Getter
@Setter
@Schema(name = "SalesOrderEditConflictProblem")
public class SalesOrderEditConflictProblem extends ApiProblemDetail {

  @Schema(description = "The save's operation id (EDIT_CONFLICT, EDIT_BASE_EXPIRED)")
  private UUID operationId;

  @Schema(description = "The base the save was made against (EDIT_CONFLICT, EDIT_BASE_EXPIRED)")
  private UUID baseId;

  @Schema(description = "The base to resolve against (EDIT_CONFLICT, EDIT_BASE_EXPIRED)")
  private SalesOrderEditBase currentBase;

  @Schema(description = "What was not saved (EDIT_CONFLICT, EDIT_BASE_EXPIRED)")
  private List<SalesOrderEditConflict> conflicts;

  protected SalesOrderEditConflictProblem() {
    super();
  }

  public static SalesOrderEditConflictProblem of(
      String code,
      String detail,
      String instance,
      UUID operationId,
      UUID baseId,
      SalesOrderEditBase currentBase,
      List<SalesOrderEditConflict> conflicts) {
    SalesOrderEditConflictProblem problem = new SalesOrderEditConflictProblem();
    problem.setStatus(409);
    problem.setTitle("Conflict");
    problem.setCode(code);
    problem.setDetail(detail);
    problem.setInstance(URI.create(instance));
    problem.operationId = operationId;
    problem.baseId = baseId;
    problem.currentBase = currentBase;
    problem.conflicts = List.copyOf(conflicts);
    return problem;
  }
}
