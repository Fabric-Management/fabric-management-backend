package com.fabricmanagement.finance.invoice.domain;

public enum TaxCategory {
  STANDARD, // UK: 20%
  REDUCED, // UK: 5%
  ZERO_RATED, // UK: 0% (still reported on VAT return)
  EXEMPT, // Outside scope of VAT
  REVERSE_CHARGE // B2B cross-border — buyer self-assesses
}
