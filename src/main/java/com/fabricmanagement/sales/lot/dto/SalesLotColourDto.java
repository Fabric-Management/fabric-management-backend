package com.fabricmanagement.sales.lot.dto;

import java.util.UUID;

public record SalesLotColourDto(
    UUID colourId, String colourCode, String colourName, String colourHex, String colourLabel) {}
