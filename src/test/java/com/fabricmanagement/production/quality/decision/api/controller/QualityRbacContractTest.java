package com.fabricmanagement.production.quality.decision.api.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.fabricmanagement.product.qualitygrade.api.QualityGradeController;
import com.fabricmanagement.production.core.batch.api.controller.BatchController;
import com.fabricmanagement.production.quality.decision.dto.RecordQualityDecisionRequest;
import com.fabricmanagement.production.quality.result.api.controller.FiberTestResultController;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

class QualityRbacContractTest {

  @Test
  void approvalAndMasterDataMutationsUseElevatedQualityActions() {
    assertAction(FiberTestResultController.class, "updateApproval", "quality', 'approve");
    assertAction(QualityGradeController.class, "create", "quality', 'manage");
    assertAction(QualityGradeController.class, "update", "quality', 'manage");
    assertAction(QualityGradeController.class, "deactivate", "quality', 'manage");
    assertAction(BatchController.class, "overrideStatus", "quality', 'approve");
  }

  @Test
  void httpDecisionContractCannotAcceptTrustedProvenanceFields() {
    var componentNames =
        Arrays.stream(RecordQualityDecisionRequest.class.getRecordComponents())
            .map(component -> component.getName())
            .collect(Collectors.toSet());

    assertThat(componentNames).doesNotContain("actorId", "origin", "sourceEventId", "decidedAt");
  }

  @Test
  void baleChoicesRequireExactlyQualityWrite() {
    assertThat(actionAnnotation(FiberTestResultController.class, "listBaleChoices"))
        .isEqualTo("@auth.can(authentication, 'quality', 'write')");
  }

  private void assertAction(Class<?> controller, String methodName, String expectedFragment) {
    assertThat(actionAnnotation(controller, methodName)).contains(expectedFragment);
  }

  private String actionAnnotation(Class<?> controller, String methodName) {
    Method method =
        Arrays.stream(controller.getDeclaredMethods())
            .filter(candidate -> candidate.getName().equals(methodName))
            .findFirst()
            .orElseThrow();
    PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);
    assertThat(annotation).isNotNull();
    return annotation.value();
  }
}
