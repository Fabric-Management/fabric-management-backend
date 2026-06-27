package com.fabricmanagement.common.infrastructure.web.exception;

/** Raised when an expired tenant attempts a write operation. */
public class TenantReadOnlyException extends DomainException {

  public static final String CODE = "TENANT_READ_ONLY";
  public static final String MESSAGE =
      "Your trial has ended — subscribe to continue. The workspace is read-only.";

  public TenantReadOnlyException() {
    super(MESSAGE, CODE, 403);
  }
}
