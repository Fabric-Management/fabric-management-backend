package com.fabricmanagement.product.fabric.domain.vocabulary;

/**
 * What colour treatment the article is designed for (FABRIC-ARCHITECTURE F6). The colour itself is
 * never on the article: it belongs to the dye lot.
 */
public enum FabricColourState {
  GREIGE,
  PFD,
  PIECE_DYED,
  YARN_DYED,
  PRINTED
}
