package com.fabricmanagement.common.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class LikePatternTest {

  @Test
  void escapesLikeWildcardsAndEscapeCharacter() {
    assertEquals("%50\\%\\_off\\\\line%", LikePattern.literalContains("50%_off\\line"));
  }
}
