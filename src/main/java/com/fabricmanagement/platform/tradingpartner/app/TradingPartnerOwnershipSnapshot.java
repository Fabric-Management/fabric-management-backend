package com.fabricmanagement.platform.tradingpartner.app;

import java.util.UUID;

/**
 * Sales-readable projection of the trading-partner facts used for commercial ownership.
 *
 * <p>This record deliberately carries no platform domain types.
 */
public record TradingPartnerOwnershipSnapshot(
    UUID customerId, UUID acquiredById, boolean customer, boolean transactionAllowed) {}
