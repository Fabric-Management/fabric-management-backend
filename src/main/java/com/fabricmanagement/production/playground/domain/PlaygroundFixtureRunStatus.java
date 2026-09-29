package com.fabricmanagement.production.playground.domain;

/** Lifecycle of one initial playground provisioning. */
public enum PlaygroundFixtureRunStatus {
  /** Eligible provisioning recorded; fixtures may be installed or repaired. */
  PENDING,
  /** Fixtures installed; every later call is a no-op. */
  COMPLETED
}
