package com.fabricmanagement.production.execution.workorder.app.validation;

/** Centralized constants for textile production parameter validation. */
public final class ProductionValidationConstants {

  private ProductionValidationConstants() {}

  public static final double MOISTURE_MIN = 0.0;
  public static final double MOISTURE_MAX = 20.0;

  public static final int GSM_MIN = 80;
  public static final int GSM_MAX = 400;

  public static final double WIDTH_MIN = 100.0;
  public static final double WIDTH_MAX = 320.0;

  public static final double TUBE_WIDTH_MIN = 30.0;
  public static final double TUBE_WIDTH_MAX = 200.0;

  public static final double PH_MIN = 2.0;
  public static final double PH_MAX = 14.0;

  public static final double STENTER_TEMP_MIN = 80.0;
  public static final double STENTER_TEMP_MAX = 250.0;

  public static final double OVERFEED_MIN = -10.0;
  public static final double OVERFEED_MAX = 30.0;

  public static final double SHRINKAGE_MIN = -15.0;
  public static final double SHRINKAGE_MAX = 15.0;
}
