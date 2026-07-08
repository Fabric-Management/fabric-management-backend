package com.fabricmanagement.sales.color.app;

import java.util.UUID;

public record SalesColorSnapshot(UUID id, String code, String name, String colorHex) {}
