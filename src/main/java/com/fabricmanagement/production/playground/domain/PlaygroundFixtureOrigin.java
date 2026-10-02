package com.fabricmanagement.production.playground.domain;

/** Trusted server-side provisioning flows allowed to install playground fibre fixtures. */
public enum PlaygroundFixtureOrigin {
  /** A newly created persisted type=PLAYGROUND tenant (legacy clone orchestration). */
  LEGACY_PLAYGROUND_CREATION,
  /** A newly created register-first REGULAR + demo_mode tenant with PLAYGROUND signup intent. */
  REGISTER_FIRST_SIGNUP
}
