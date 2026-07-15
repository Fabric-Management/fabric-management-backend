package com.fabricmanagement.production.masterdata.color.dto;

import com.fabricmanagement.production.masterdata.color.domain.ColorFamily;
import com.fabricmanagement.production.masterdata.color.domain.ColorStandardStatus;
import com.fabricmanagement.production.masterdata.color.domain.ColorType;
import com.fabricmanagement.production.masterdata.color.domain.DeltaEFormula;
import com.fabricmanagement.production.masterdata.color.domain.LabIlluminant;
import com.fabricmanagement.production.masterdata.color.domain.LabObserver;
import com.fabricmanagement.production.masterdata.color.domain.PantoneSystem;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.UUID;

public record ColorDto(
    @Schema(description = "Tenant-owned color-card identifier") UUID id,
    @Schema(description = "Canonical tenant-unique code, stored trimmed and uppercase") String code,
    @Schema(description = "Human-readable color-card name, stored trimmed") String name,
    @Schema(description = "Screen approximation only; never an authoritative colour standard")
        String colorHex,
    @Schema(description = "Process classification of the color card") ColorType colorType,
    @Schema(description = "Optional external Pantone reference, stored trimmed and uppercase")
        String pantoneCode,
    @Schema(description = "Pantone carrier system; null when no Pantone reference exists")
        PantoneSystem pantoneSystem,
    @Schema(description = "Display-ready Pantone reference such as '19-4024 TCX'")
        String pantoneLabel,
    @Schema(description = "Visual family used for classification and filtering")
        ColorFamily colorFamily,
    @Schema(description = "Target CIE Lab lightness L*, from 0 to 100; not a measured batch value")
        BigDecimal targetLabL,
    @Schema(description = "Target CIE Lab green-red axis a*, from -128 to 127")
        BigDecimal targetLabA,
    @Schema(description = "Target CIE Lab blue-yellow axis b*, from -128 to 127")
        BigDecimal targetLabB,
    @Schema(description = "Illuminant under which the target Lab values are defined")
        LabIlluminant targetLabIlluminant,
    @Schema(description = "Standard observer angle used for the target Lab values")
        LabObserver targetLabObserver,
    @Schema(description = "Maximum accepted Delta-E; requires a target and a formula")
        BigDecimal deltaETolerance,
    @Schema(description = "Formula used to calculate Delta-E against the target")
        DeltaEFormula deltaEFormula,
    @Schema(description = "Shade-standard lifecycle state; changed only by transition endpoints")
        ColorStandardStatus standardStatus,
    @Schema(description = "Optional internal notes about the color card") String notes,
    @Schema(description = "Whether the color card is active; false means soft-deleted")
        boolean active) {}
