package com.fabricmanagement.product.yarn.domain.article;

import com.fabricmanagement.product.core.domain.registry.policy.LinearDensityV1;
import com.fabricmanagement.product.core.domain.registry.policy.TwistV1;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Produces AuditSpecSnapshot-v1 and projects it into CanonicalIdentityProjection-v1. */
public class YarnArticleSpecSerializer {

  public static final String AUDIT_SCHEMA = "AuditSpecSnapshot-v1";
  public static final String IDENTITY_SCHEMA = "CanonicalIdentityProjection-v1";

  private final ObjectMapper objectMapper;

  public YarnArticleSpecSerializer(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public ObjectNode auditSnapshot(YarnArticle article) {
    ObjectNode root = objectMapper.createObjectNode();
    root.put("documentType", AUDIT_SCHEMA);
    putUuid(root, "articleId", article.getId());
    putUuid(root, "productId", article.getProductId());
    root.put("articleSpecVersion", article.getArticleSpecVersion());
    putEnum(root, "status", article.getStatus());
    root.put("name", article.getName());
    putText(root, "description", article.getDescription());
    putEnum(root, "originalCountSystem", article.getOriginalCountSystem());
    putDecimal(root, "originalCountValue", article.getOriginalCountValue(), null);
    putEnum(root, "countBasis", article.getCountBasis());
    putEnum(root, "structureType", article.getStructureType());
    putInteger(root, "foldCount", article.getFoldCount());
    putInteger(root, "filamentCount", article.getFilamentCount());
    putDecimal(root, "twistContractionPercent", article.getTwistContractionPercent(), 2);
    putDecimal(root, "resultantLinearDensityTex", article.getResultantLinearDensityTex(), 2);
    putText(root, "canonicalDesignation", article.getCanonicalDesignation());
    putText(root, "sourceDesignation", article.getSourceDesignation());
    putEnum(root, "materialForm", article.getMaterialForm());
    putEnum(root, "spinningTechnologyFamily", article.getSpinningTechnologyFamily());
    ObjectNode spinningSystem = objectMapper.createObjectNode();
    if (article.getSpinningSystemRef() != null) {
      putUuid(spinningSystem, "id", article.getSpinningSystemRef().getId());
      spinningSystem.put("code", article.getSpinningSystemRef().getCode());
      root.set("spinningSystemRef", spinningSystem);
    } else {
      root.putNull("spinningSystemRef");
    }
    putEnum(root, "filamentForm", article.getFilamentForm());
    putText(root, "canonicalKey", article.getCanonicalKey());
    root.put("canonicalKeyAlgorithmVersion", article.getCanonicalKeyAlgorithmVersion());

    ArrayNode features = root.putArray("constructionFeatures");
    article.getConstructionFeatures().stream()
        .map(YarnArticleConstructionFeature::getFeature)
        .sorted()
        .forEach(feature -> features.add(feature.name()));

    ArrayNode composition = root.putArray("composition");
    article.getComposition().stream()
        .sorted(Comparator.comparing(row -> row.getFiberId().toString()))
        .map(this::compositionSnapshot)
        .forEach(composition::add);

    ArrayNode components = root.putArray("structureComponents");
    article.getStructureComponents().stream()
        .sorted(
            Comparator.comparing(YarnArticleStructureComponent::getKind)
                .thenComparingInt(YarnArticleStructureComponent::getComponentIndex))
        .map(this::componentSnapshot)
        .forEach(components::add);

    ArrayNode stages = root.putArray("twistStages");
    article.getTwistStages().stream()
        .sorted(Comparator.comparingInt(YarnArticleTwistStage::getSequence))
        .map(this::stageSnapshot)
        .forEach(stages::add);

    ObjectNode policies = root.putObject("conversionPolicies");
    policies.put("linearDensity", LinearDensityV1.POLICY_NAME);
    policies.put("twist", TwistV1.POLICY_NAME);
    return root;
  }

  /** The sole hash-input path: an allowlisted projection over AuditSpecSnapshot-v1. */
  public ObjectNode identityProjection(JsonNode snapshot) {
    if (!AUDIT_SCHEMA.equals(snapshot.path("documentType").asText())) {
      throw new IllegalArgumentException("Expected " + AUDIT_SCHEMA);
    }
    ObjectNode result = objectMapper.createObjectNode();
    copy(result, snapshot, "countBasis");
    copy(result, snapshot, "structureType");
    copy(result, snapshot, "foldCount");
    copy(result, snapshot, "filamentCount");
    copy(result, snapshot, "filamentForm");
    copy(result, snapshot, "originalCountSystem");
    copy(result, snapshot, "originalCountValue");
    copy(result, snapshot, "twistContractionPercent");
    copy(result, snapshot, "resultantLinearDensityTex");
    copy(result, snapshot, "materialForm");
    copy(result, snapshot, "spinningTechnologyFamily");
    JsonNode spinningRef = snapshot.get("spinningSystemRef");
    result.set(
        "spinningSystemCode",
        spinningRef == null || spinningRef.isNull()
            ? objectMapper.nullNode()
            : spinningRef.get("code").deepCopy());
    result.set("constructionFeatures", snapshot.get("constructionFeatures").deepCopy());

    ArrayNode composition = result.putArray("composition");
    snapshot
        .withArray("composition")
        .forEach(
            row -> {
              ObjectNode projected = objectMapper.createObjectNode();
              copy(projected, row, "fiberId");
              copy(projected, row, "percentage");
              copy(projected, row, "materialSource");
              composition.add(projected);
            });

    ArrayNode components = result.putArray("structureComponents");
    snapshot
        .withArray("structureComponents")
        .forEach(
            row -> {
              ObjectNode projected = objectMapper.createObjectNode();
              copy(projected, row, "kind");
              copy(projected, row, "layerRole");
              copy(projected, row, "componentIndex");
              copy(projected, row, "componentCountSystem");
              copy(projected, row, "componentCountValue");
              copy(projected, row, "componentLinearDensityTex");
              copy(projected, row, "fiberId");
              components.add(projected);
            });

    ArrayNode stages = result.putArray("twistStages");
    snapshot
        .withArray("twistStages")
        .forEach(
            row -> {
              ObjectNode projected = objectMapper.createObjectNode();
              copy(projected, row, "sequence");
              copy(projected, row, "stageType");
              copy(projected, row, "direction");
              copy(projected, row, "turnsPerMeter");
              copy(projected, row, "strandComponentIndex");
              copy(projected, row, "testMethodCode");
              stages.add(projected);
            });
    result.set("conversionPolicies", snapshot.get("conversionPolicies").deepCopy());
    return result;
  }

  public String canonicalKeyIfComplete(YarnArticle article) {
    if (!YarnArticleInvariantCatalog.validateFull(article).isEmpty()) {
      return null;
    }
    return sha256(identityProjectionBytes(auditSnapshot(article)));
  }

  public byte[] identityProjectionBytes(JsonNode snapshot) {
    try {
      return objectMapper.writeValueAsBytes(identityProjection(snapshot));
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Cannot serialize yarn identity projection", exception);
    }
  }

  public String canonicalKey(JsonNode snapshot) {
    return sha256(identityProjectionBytes(snapshot));
  }

  public ObjectNode changedSummary(JsonNode before, JsonNode after) {
    ObjectNode summary = objectMapper.createObjectNode();
    if (before == null) {
      ObjectNode change = summary.putObject("article");
      change.putNull("old");
      change.set("new", after.deepCopy());
      return summary;
    }
    List<String> names = new ArrayList<>();
    after.fieldNames().forEachRemaining(names::add);
    for (String name : names) {
      JsonNode oldValue = before.get(name);
      JsonNode newValue = after.get(name);
      if (!java.util.Objects.equals(oldValue, newValue)) {
        ObjectNode change = summary.putObject(name);
        change.set("old", oldValue == null ? objectMapper.nullNode() : oldValue.deepCopy());
        change.set("new", newValue == null ? objectMapper.nullNode() : newValue.deepCopy());
      }
    }
    return summary;
  }

  private ObjectNode compositionSnapshot(YarnArticleComposition row) {
    ObjectNode node = objectMapper.createObjectNode();
    putUuid(node, "fiberId", row.getFiberId());
    putDecimal(node, "percentage", row.getPercentage(), 2);
    node.put("fiberIsoCode", row.getFiberIsoCode());
    node.put("fiberName", row.getFiberName());
    putEnum(node, "materialSource", row.getMaterialSource());
    return node;
  }

  private ObjectNode componentSnapshot(YarnArticleStructureComponent row) {
    ObjectNode node = objectMapper.createObjectNode();
    putEnum(node, "kind", row.getKind());
    putEnum(node, "layerRole", row.getLayerRole());
    node.put("componentIndex", row.getComponentIndex());
    putEnum(node, "componentCountSystem", row.getComponentCountSystem());
    putDecimal(node, "componentCountValue", row.getComponentCountValue(), null);
    putDecimal(node, "componentLinearDensityTex", row.getComponentLinearDensityTex(), 2);
    putUuid(node, "fiberId", row.getFiberId());
    putText(node, "fiberIsoCode", row.getFiberIsoCode());
    putText(node, "fiberName", row.getFiberName());
    putEnum(node, "materialSource", row.getMaterialSource());
    putText(node, "label", row.getLabel());
    return node;
  }

  private ObjectNode stageSnapshot(YarnArticleTwistStage row) {
    ObjectNode node = objectMapper.createObjectNode();
    node.put("sequence", row.getSequence());
    putEnum(node, "stageType", row.getStageType());
    putEnum(node, "direction", row.getDirection());
    putDecimal(node, "turnsPerMeter", row.getTurnsPerMeter(), 2);
    putInteger(node, "strandComponentIndex", row.getStrandComponentIndex());
    putUuid(node, "testMethodId", row.getTestMethodId());
    putText(
        node, "testMethodCode", row.getTestMethod() == null ? null : row.getTestMethod().getCode());
    return node;
  }

  private static void copy(ObjectNode target, JsonNode source, String name) {
    JsonNode value = source.get(name);
    target.set(name, value == null ? target.nullNode() : value.deepCopy());
  }

  private static void putText(ObjectNode node, String name, String value) {
    if (value == null) node.putNull(name);
    else node.put(name, value);
  }

  private static void putUuid(ObjectNode node, String name, java.util.UUID value) {
    putText(node, name, value == null ? null : value.toString().toLowerCase(Locale.ROOT));
  }

  private static void putEnum(ObjectNode node, String name, Enum<?> value) {
    putText(node, name, value == null ? null : value.name());
  }

  private static void putInteger(ObjectNode node, String name, Integer value) {
    if (value == null) node.putNull(name);
    else node.put(name, value);
  }

  private static void putDecimal(ObjectNode node, String name, BigDecimal value, Integer scale) {
    if (value == null) {
      node.putNull(name);
      return;
    }
    BigDecimal normalized =
        scale == null
            ? new BigDecimal(value.stripTrailingZeros().toPlainString())
            : value.setScale(scale, RoundingMode.HALF_UP);
    node.put(name, normalized);
  }

  private static String sha256(byte[] bytes) {
    try {
      return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }
}
