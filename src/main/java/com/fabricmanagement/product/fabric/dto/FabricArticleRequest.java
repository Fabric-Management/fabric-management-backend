package com.fabricmanagement.product.fabric.dto;

import com.fabricmanagement.product.fabric.domain.vocabulary.FabricFormation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Create or replace a fabric article. The formation is chosen at creation and cannot change; an
 * update must repeat it. The name and description are the product's own (FABRIC-0).
 */
@Schema(name = "FabricArticleRequest")
public record FabricArticleRequest(
    @NotBlank
        @Size(max = 255)
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "Cotton Poplin 120gsm")
        String name,
    @Size(max = 4000) String description,
    @NotNull @Schema(requiredMode = Schema.RequiredMode.REQUIRED) FabricFormation formation,
    @Size(max = 60) @Schema(example = "PM-1454") String articleCode,
    UUID constructionId,
    @DecimalMin(value = "0", inclusive = false) @Schema(example = "120") BigDecimal gsmFinished,
    @DecimalMin(value = "0", inclusive = false) BigDecimal gsmGreige,
    @DecimalMin(value = "0", inclusive = false) @Schema(example = "150")
        BigDecimal nominalFinishedWidthCm,
    @DecimalMin(value = "0", inclusive = false) BigDecimal usableWidthCm,
    @DecimalMin(value = "0", inclusive = false) BigDecimal nominalGreigeWidthCm,
    @DecimalMin(value = "0", inclusive = false) @Schema(example = "120")
        BigDecimal standardRollLengthM,
    @Size(max = 4000) String remarks,
    @Valid @Schema(nullable = true, description = "Woven fabrics only") FabricWovenDto woven,
    @Valid @Schema(nullable = true, description = "Knitted fabrics only") FabricKnittedDto knitted,
    @Size(max = 20)
        @ArraySchema(
            arraySchema =
                @Schema(
                    description =
                        "The fabric's yarns; when given, their shares must add up to 100 before"
                            + " activation. Omitted on update keeps the current yarns; an empty"
                            + " list removes them."))
        List<@Valid @NotNull FabricYarnRequest> yarns,
    @Size(max = 20)
        @ArraySchema(
            arraySchema =
                @Schema(
                    description =
                        "The declared fibre composition; it must add up to 100 before activation."
                            + " Omitted on update keeps the current composition; an empty list"
                            + " removes it."))
        List<@Valid @NotNull FabricCompositionRequest> composition) {}
