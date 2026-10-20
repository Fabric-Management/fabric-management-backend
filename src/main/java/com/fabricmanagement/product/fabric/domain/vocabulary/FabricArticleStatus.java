package com.fabricmanagement.product.fabric.domain.vocabulary;

/**
 * Article lifecycle (yarn Y1 parity). Only ACTIVE articles feed new documents; a DRAFT has never
 * been used, so it may be deleted; an ACTIVE article is retired as OBSOLETE, never deleted.
 */
public enum FabricArticleStatus {
  DRAFT,
  ACTIVE,
  OBSOLETE
}
