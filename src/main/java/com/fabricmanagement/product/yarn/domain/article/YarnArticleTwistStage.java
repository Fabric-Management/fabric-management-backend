package com.fabricmanagement.product.yarn.domain.article;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import com.fabricmanagement.product.yarn.domain.exception.YarnDomainException;
import com.fabricmanagement.product.yarn.domain.reference.YarnTestMethod;
import com.fabricmanagement.product.yarn.domain.vocabulary.TwistDirection;
import com.fabricmanagement.product.yarn.domain.vocabulary.TwistStageType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "prod_yarn_article_twist_stage",
    schema = "production",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uq_yarn_article_twist_sequence",
            columnNames = {"tenant_id", "article_id", "sequence"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class YarnArticleTwistStage extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "article_id", nullable = false, updatable = false)
  private YarnArticle article;

  @Enumerated(EnumType.STRING)
  @Column(name = "stage_type", nullable = false, length = 20, updatable = false)
  private TwistStageType stageType;

  @Column(name = "sequence", nullable = false, updatable = false)
  private int sequence;

  @Enumerated(EnumType.STRING)
  @Column(name = "direction", nullable = false, length = 10, updatable = false)
  private TwistDirection direction;

  @Column(name = "turns_per_meter", nullable = false, precision = 18, scale = 2, updatable = false)
  private BigDecimal turnsPerMeter;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "component_id", updatable = false)
  private YarnArticleStructureComponent component;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "test_method_id", updatable = false)
  private YarnTestMethod testMethod;

  static YarnArticleTwistStage create(
      YarnArticle article,
      YarnArticleSpec.TwistStageInput input,
      YarnArticleStructureComponent component) {
    if (input.turnsPerMeter() != null && input.turnsPerMeter().signum() < 0) {
      throw new YarnDomainException("I29", "I29: turnsPerMeter cannot be negative");
    }
    YarnArticleTwistStage row = new YarnArticleTwistStage();
    row.article = article;
    row.stageType = input.stageType();
    row.sequence = input.sequence();
    row.direction = input.direction();
    row.turnsPerMeter =
        input.turnsPerMeter() == null
            ? null
            : input.turnsPerMeter().setScale(2, RoundingMode.HALF_UP);
    row.component = component;
    row.testMethod = input.testMethod();
    return row;
  }

  public Integer getStrandComponentIndex() {
    return component == null ? null : component.getComponentIndex();
  }

  public UUID getTestMethodId() {
    return testMethod == null ? null : testMethod.getId();
  }

  @Override
  protected String getModuleCode() {
    return "YTWS";
  }
}
