package com.fabricmanagement.sales.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Order-intake rule violations (SOI). Each code names the business rule it enforces so the frontend
 * can explain the refusal without re-deriving it.
 */
public class OrderIntakeException extends SalesDomainException {

  public OrderIntakeException(String message, String code, HttpStatus status) {
    super(message, code, status);
  }

  public static OrderIntakeException productRequired() {
    return new OrderIntakeException(
        "A catalogue line must name a product; a description never replaces it",
        "ORDER_INTAKE_PRODUCT_REQUIRED",
        HttpStatus.BAD_REQUEST);
  }

  public static OrderIntakeException productNotAvailable(Object productId) {
    return new OrderIntakeException(
        "Product is not an active product of this tenant: " + productId,
        "ORDER_INTAKE_PRODUCT_NOT_AVAILABLE",
        HttpStatus.UNPROCESSABLE_ENTITY);
  }

  public static OrderIntakeException productTypeNotSellable(Object productType) {
    return new OrderIntakeException(
        "Only fibre, yarn and fabric products can be ordered here: " + productType,
        "ORDER_INTAKE_PRODUCT_TYPE_NOT_SELLABLE",
        HttpStatus.UNPROCESSABLE_ENTITY);
  }

  public static OrderIntakeException productNotVisible(Object productId) {
    return new OrderIntakeException(
        "Product is private to another customer: " + productId,
        "ORDER_INTAKE_PRODUCT_NOT_VISIBLE",
        HttpStatus.UNPROCESSABLE_ENTITY);
  }

  public static OrderIntakeException unitNotAllowed(String unit) {
    return new OrderIntakeException(
        "Unit is not a sales unit of this product: " + unit,
        "ORDER_INTAKE_UNIT_NOT_ALLOWED",
        HttpStatus.UNPROCESSABLE_ENTITY);
  }

  public static OrderIntakeException colorNotDefined(Object colorId) {
    return new OrderIntakeException(
        "Colour is not an active colour card: " + colorId,
        "ORDER_INTAKE_COLOR_NOT_DEFINED",
        HttpStatus.UNPROCESSABLE_ENTITY);
  }

  public static OrderIntakeException widthRequired() {
    return new OrderIntakeException(
        "This product is sold in defined finished widths; choose one",
        "ORDER_INTAKE_WIDTH_REQUIRED",
        HttpStatus.UNPROCESSABLE_ENTITY);
  }

  public static OrderIntakeException widthNotDefined(Object width, String unit) {
    return new OrderIntakeException(
        "Finished width is not defined for this product: " + width + " " + unit,
        "ORDER_INTAKE_WIDTH_NOT_DEFINED",
        HttpStatus.UNPROCESSABLE_ENTITY);
  }

  public static OrderIntakeException duplicateDistribution() {
    return new OrderIntakeException(
        "The order already has this distribution (product, colour, width, unit, delivery date)",
        "ORDER_INTAKE_DUPLICATE_DISTRIBUTION",
        HttpStatus.CONFLICT);
  }

  public static OrderIntakeException stale(String what) {
    return new OrderIntakeException(
        what + " changed since it was read; reload and decide again",
        "ORDER_INTAKE_STALE",
        HttpStatus.CONFLICT);
  }

  public static OrderIntakeException notFound(String what, Object id) {
    return new OrderIntakeException(
        what + " not found: " + id, "ORDER_INTAKE_NOT_FOUND", HttpStatus.NOT_FOUND);
  }

  public static OrderIntakeException rule(String code, String message) {
    return new OrderIntakeException(
        message, "ORDER_INTAKE_" + code, HttpStatus.UNPROCESSABLE_ENTITY);
  }

  public static OrderIntakeException conflict(String code, String message) {
    return new OrderIntakeException(message, "ORDER_INTAKE_" + code, HttpStatus.CONFLICT);
  }
}
