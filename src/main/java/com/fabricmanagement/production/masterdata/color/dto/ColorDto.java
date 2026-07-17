package com.fabricmanagement.production.masterdata.color.dto;

import com.fabricmanagement.production.masterdata.color.domain.ColorFamily;
import com.fabricmanagement.production.masterdata.color.domain.ColorStandardStatus;
import com.fabricmanagement.production.masterdata.color.domain.ColorType;
import com.fabricmanagement.production.masterdata.color.domain.DeltaEFormula;
import com.fabricmanagement.production.masterdata.color.domain.LabIlluminant;
import com.fabricmanagement.production.masterdata.color.domain.LabObserver;
import com.fabricmanagement.production.masterdata.color.domain.PantoneSystem;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.UUID;

@Schema(
    description =
        "Complete color-card representation. Every property is present; optional values are explicit nulls.")
@JsonInclude(JsonInclude.Include.ALWAYS)
public record ColorDto(
    @Schema(
            description = "Tenant-owned color-card identifier",
            requiredMode = Schema.RequiredMode.REQUIRED)
        UUID id,
    @Schema(
            description = "Canonical tenant-unique code, stored trimmed and uppercase",
            requiredMode = Schema.RequiredMode.REQUIRED)
        String code,
    @Schema(
            description = "Human-readable color-card name, stored trimmed",
            requiredMode = Schema.RequiredMode.REQUIRED)
        String name,
    @Schema(
            description = "Screen approximation only; never an authoritative colour standard",
            requiredMode = Schema.RequiredMode.REQUIRED,
            nullable = true)
        String colorHex,
    @Schema(
            description = "Process classification of the color card",
            requiredMode = Schema.RequiredMode.REQUIRED)
        ColorType colorType,
    @Schema(
            description = "Optional external Pantone reference, stored trimmed and uppercase",
            requiredMode = Schema.RequiredMode.REQUIRED,
            nullable = true)
        String pantoneCode,
    @Schema(
            description = "Pantone carrier system; null when no Pantone reference exists",
            requiredMode = Schema.RequiredMode.REQUIRED,
            nullable = true)
        PantoneSystem pantoneSystem,
    @Schema(
            description = "Display-ready Pantone reference such as '19-4024 TCX'",
            requiredMode = Schema.RequiredMode.REQUIRED,
            nullable = true)
        String pantoneLabel,
    @Schema(
            description = "Visual family used for classification and filtering",
            requiredMode = Schema.RequiredMode.REQUIRED)
        ColorFamily colorFamily,
    @Schema(
            description =
                "Target CIE Lab lightness L*, from 0 to 100; null when no target is defined",
            requiredMode = Schema.RequiredMode.REQUIRED,
            nullable = true)
        BigDecimal targetLabL,
    @Schema(
            description = "Target CIE Lab green-red axis a*, from -128 to 127; null with no target",
            requiredMode = Schema.RequiredMode.REQUIRED,
            nullable = true)
        BigDecimal targetLabA,
    @Schema(
            description =
                "Target CIE Lab blue-yellow axis b*, from -128 to 127; null with no target",
            requiredMode = Schema.RequiredMode.REQUIRED,
            nullable = true)
        BigDecimal targetLabB,
    @Schema(
            description =
                "Illuminant under which the target Lab values are defined; null with no target",
            requiredMode = Schema.RequiredMode.REQUIRED,
            nullable = true)
        LabIlluminant targetLabIlluminant,
    @Schema(
            description =
                "Standard observer angle used for the target Lab values; null with no target",
            requiredMode = Schema.RequiredMode.REQUIRED,
            nullable = true)
        LabObserver targetLabObserver,
    @Schema(
            description = "Maximum accepted Delta-E; null when no tolerance is configured",
            requiredMode = Schema.RequiredMode.REQUIRED,
            nullable = true)
        BigDecimal deltaETolerance,
    @Schema(
            description = "Formula used to calculate Delta-E; null when no tolerance is configured",
            requiredMode = Schema.RequiredMode.REQUIRED,
            nullable = true)
        DeltaEFormula deltaEFormula,
    @Schema(
            description = "Shade-standard lifecycle state; changed only by transition endpoints",
            requiredMode = Schema.RequiredMode.REQUIRED)
        ColorStandardStatus standardStatus,
    @Schema(
            description = "Optional internal notes about the color card",
            requiredMode = Schema.RequiredMode.REQUIRED,
            nullable = true)
        String notes,
    @Schema(
            description = "Whether the color card is active; false means soft-deleted",
            requiredMode = Schema.RequiredMode.REQUIRED)
        boolean active) {}
