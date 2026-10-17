package com.fabricmanagement.platform.tenant.domain;

/** Outlines already validated local text, refusing font substitution or missing glyphs. */
public interface BrandSvgOutlinePort {
  String outline(String svg);
}
