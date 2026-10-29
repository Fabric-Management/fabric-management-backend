package com.fabricmanagement.production.core.batch.domain;

import java.math.BigDecimal;
import java.util.UUID;

/** Metres of a finishing step that came back in one quality grade. */
public record FinishingGradeMetres(UUID qualityGradeId, BigDecimal metres) {}
