package com.fabricmanagement.procurement.rfq.domain;

/**
 * RFQ Alıcı statüleri.
 *
 * <p>Fix #9 — Yeni eklenen alıcı henüz gönderilmediği için PENDING ile başlamalı. SENT ancak {@code
 * sendRfq()} çağrısından sonra set edilmeli.
 */
public enum RfqRecipientStatus {
  /** Alıcı eklendi ama RFQ henüz gönderilmedi. */
  PENDING,

  /** RFQ bu alıcıya başarıyla gönderildi. */
  SENT,

  /** Alıcıdan teklif alındı. */
  QUOTE_RECEIVED,

  /** Deadline doldu, alıcıdan yanıt gelmedi. */
  NO_RESPONSE
}
