package com.fabricmanagement.sales.salesorder.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** Who holds the order's edit form open now (CEDIT-06 §3). */
@Schema(
    name = "SalesOrderEditorsDto",
    description =
        "Open edit sessions of the order now, oldest first, including the caller's own. Viewers"
            + " who only read the order are not listed.")
public record SalesOrderEditorsDto(
    @ArraySchema(
            arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED),
            schema = @Schema(implementation = SalesOrderEditorDto.class))
        List<SalesOrderEditorDto> editors) {

  public SalesOrderEditorsDto {
    editors = editors == null ? List.of() : List.copyOf(editors);
  }
}
