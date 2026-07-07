package com.fabricmanagement.sales.quote.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record QuoteLineLotSelectionRequest(@NotNull UUID lotId, List<UUID> stockUnitIds) {}
