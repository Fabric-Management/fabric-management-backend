package com.fabricmanagement.procurement.purchaseorder.app.validation;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Constants used across module-specific PurchaseOrder validation strategies. Defines
 * industry-standard ranges and boundaries for textile parameters.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class TextileValidationConstants {

  // Fiber
  public static final double MICRONAIRE_MIN = 2.5;
  public static final double MICRONAIRE_MAX = 6.0;
  public static final double MOISTURE_MIN = 0.0;
  public static final double MOISTURE_MAX = 20.0;
  public static final double TRASH_MIN = 0.0;
  public static final double TRASH_MAX = 10.0;
  public static final double UNIFORMITY_MIN = 0.0;
  public static final double UNIFORMITY_MAX = 100.0;

  public static final int COLOR_GRADE_MAX_LENGTH = 20;
  public static final int CROP_YEAR_MAX_LENGTH = 15;
  public static final int ORIGIN_MAX_LENGTH = 50;

  // Yarn
  public static final double USTER_MIN = 0.0;
  public static final double USTER_MAX = 100.0;

  // Fabric
  public static final int GSM_MIN = 80;
  public static final int GSM_MAX = 400;
  public static final int WIDTH_CM_MIN = 100;
  public static final int WIDTH_CM_MAX = 320;
  public static final double SHRINKAGE_MIN = -15.0;
  public static final double SHRINKAGE_MAX = 15.0;

  // Dye
  public static final int LIGHTNESS_MAX_LENGTH = 20;
}
