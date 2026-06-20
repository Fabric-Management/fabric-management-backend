package com.fabricmanagement.platform.organization.domain;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Canonical registry for system department codes used across seeding and permission templates. */
public enum SystemDepartment {
  PRODUCTION("PRODUCTION", "Production", "Production-related departments", true, false, 1),
  ADMINISTRATION(
      "ADMINISTRATION",
      "Administration",
      "Administrative and management departments",
      true,
      false,
      2),
  LOGISTICS("LOGISTICS", "Logistics", "Logistics and supply chain departments", true, false, 3),
  UTILITY("UTILITY", "Utility", "Auxiliary service departments", true, false, 4),
  SUPPORT("SUPPORT", "Support", "Support and service departments", true, false, 5),

  RD(
      "RD",
      "R&D / Product Development",
      "Research, development and fiber formula design",
      false,
      false,
      10),
  PLANNING(
      "PLANNING",
      "Production Planning",
      "Production scheduling, capacity planning and work orders",
      false,
      false,
      11),
  FIBER(
      "FIBER",
      "Fiber & Raw Product",
      "Fiber procurement and raw product management",
      false,
      true,
      12),
  YARN("YARN", "Yarn Production", "Yarn manufacturing operations", false, true, 13),
  WEAVING("WEAVING", "Weaving", "Fabric weaving operations", false, true, 14),
  KNITTING("KNITTING", "Knitting", "Fabric knitting operations", false, true, 15),
  DYEING("DYEING", "Dyeing & Finishing", "Fabric dyeing and finishing operations", false, true, 16),
  GARMENT(
      "GARMENT", "Garment Manufacturing", "Garment cutting, sewing and packing", false, true, 17),
  QUALITY(
      "QUALITY", "Quality Control", "Quality assurance and laboratory testing", false, true, 18),

  HR("HR", "Human Resources", "Human resources management", false, true, 20),
  FINANCE(
      "FINANCE", "Finance & Accounting", "Financial management and accounting", false, true, 21),
  SALES(
      "SALES",
      "Sales & Marketing",
      "Sales, merchandising and customer account management",
      false,
      true,
      22),
  ADMINISTRATION_OFFICE(
      "ADMINISTRATION_OFFICE",
      "Administration Office",
      "General administration and office management",
      false,
      false,
      23),
  MANAGEMENT_PLANNING(
      "MANAGEMENT_PLANNING",
      "Management & Planning",
      "Executive management and strategic planning",
      false,
      false,
      24),

  WAREHOUSE("WAREHOUSE", "Warehouse", "Warehouse management and storage", false, true, 30),
  PROCUREMENT(
      "PROCUREMENT",
      "Procurement & Supply",
      "Procurement and supply chain management",
      false,
      true,
      31),
  SHIPPING(
      "SHIPPING",
      "Shipping & Transport",
      "Shipping and transportation management",
      false,
      false,
      32),

  MAINTENANCE("MAINTENANCE", "Maintenance", "Equipment maintenance and repair", false, false, 40),
  ENERGY_FACILITIES(
      "ENERGY_FACILITIES",
      "Energy & Facilities",
      "Energy generation and facility operations",
      false,
      false,
      41),
  KITCHEN_CATERING(
      "KITCHEN_CATERING", "Kitchen & Catering", "Kitchen and cafeteria services", false, false, 42),

  IT_SERVICES(
      "IT_SERVICES", "IT Services", "IT support and system administration", false, false, 50),
  SECURITY("SECURITY", "Security", "Security and access control", false, false, 51),
  CLEANING_SERVICES(
      "CLEANING_SERVICES",
      "Cleaning Services",
      "Cleaning and janitorial services",
      false,
      false,
      52);

  private static final Map<String, SystemDepartment> BY_CODE =
      Arrays.stream(values())
          .collect(Collectors.toUnmodifiableMap(SystemDepartment::code, Function.identity()));

  private final String code;
  private final String displayName;
  private final String description;
  private final boolean group;
  private final boolean permissionBacked;
  private final int displayOrder;

  SystemDepartment(
      String code,
      String displayName,
      String description,
      boolean group,
      boolean permissionBacked,
      int displayOrder) {
    this.code = code;
    this.displayName = displayName;
    this.description = description;
    this.group = group;
    this.permissionBacked = permissionBacked;
    this.displayOrder = displayOrder;
  }

  public String code() {
    return code;
  }

  public String displayName() {
    return displayName;
  }

  public String description() {
    return description;
  }

  public boolean isGroup() {
    return group;
  }

  public boolean isPermissionBacked() {
    return permissionBacked;
  }

  public int displayOrder() {
    return displayOrder;
  }

  public static List<SystemDepartment> permissionBackedDepartments() {
    return Arrays.stream(values()).filter(SystemDepartment::isPermissionBacked).toList();
  }

  public static List<String> permissionBackedCodes() {
    return permissionBackedDepartments().stream().map(SystemDepartment::code).toList();
  }

  public static Optional<SystemDepartment> fromCode(String code) {
    if (code == null) {
      return Optional.empty();
    }
    return Optional.ofNullable(BY_CODE.get(code.toUpperCase(Locale.ENGLISH)));
  }
}
