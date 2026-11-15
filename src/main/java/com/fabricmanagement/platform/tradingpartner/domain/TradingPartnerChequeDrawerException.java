package com.fabricmanagement.platform.tradingpartner.domain;

import com.fabricmanagement.common.infrastructure.web.exception.DomainException;

/** Refusals of the cheque-drawer list, each with a code a screen can explain. */
public final class TradingPartnerChequeDrawerException extends DomainException {

  private TradingPartnerChequeDrawerException(String code, String message, int status) {
    super(message, code, status);
  }

  /** Another drawer of the same customer already has this name (active or not). */
  public static TradingPartnerChequeDrawerException nameTaken() {
    return new TradingPartnerChequeDrawerException(
        "CHEQUE_DRAWER_NAME_TAKEN", "Another cheque drawer of this customer has this name", 409);
  }

  /** Cheque drawers belong to customers; a supplier-only partner hands over no cheques. */
  public static TradingPartnerChequeDrawerException notCustomer() {
    return new TradingPartnerChequeDrawerException(
        "CHEQUE_DRAWER_PARTNER_NOT_CUSTOMER",
        "Cheque drawers can be added only to a customer",
        409);
  }

  /** The partner was removed; its history stays readable but its list no longer grows. */
  public static TradingPartnerChequeDrawerException partnerRemoved() {
    return new TradingPartnerChequeDrawerException(
        "CHEQUE_DRAWER_PARTNER_REMOVED",
        "This customer was removed; its drawers cannot change",
        409);
  }

  public static TradingPartnerChequeDrawerException invalidName(String message) {
    return new TradingPartnerChequeDrawerException("CHEQUE_DRAWER_NAME_INVALID", message, 400);
  }
}
