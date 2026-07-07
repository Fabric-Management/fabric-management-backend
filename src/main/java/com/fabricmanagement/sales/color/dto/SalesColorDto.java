package com.fabricmanagement.sales.color.dto;

import java.util.UUID;

public record SalesColorDto(UUID id, String code, String name, String colorHex, boolean active) {}
