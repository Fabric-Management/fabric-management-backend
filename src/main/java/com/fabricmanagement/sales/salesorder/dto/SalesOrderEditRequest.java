package com.fabricmanagement.sales.salesorder.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/**
 * A safe-edit save (CEDIT-02 §4.2): the changes made against one server base, with a client
 * operation id that is the same when the same save is retried. Only changed keys are sent; there is
 * no order version, no base value and no full line list. The maximum number of line operations is a
 * server setting and is published as {@code maxItems}.
 */
@JsonIgnoreProperties(ignoreUnknown = false)
@Schema(
    name = "SalesOrderEditRequest",
    additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public class SalesOrderEditRequest {

  private UUID operationId;
  private UUID baseId;
  private SalesOrderHeaderEdits header;
  private List<SalesOrderLineEdit> lines;
  private List<SalesOrderEditResolution> resolutions;
  private UUID editSessionId;
  private List<UUID> leaseTokens;

  @NotNull(message = "Every save carries its operation id")
  @Schema(
      requiredMode = Schema.RequiredMode.REQUIRED,
      description = "Same id for a retry of the same save; a new id for changed content")
  public UUID getOperationId() {
    return operationId;
  }

  public void setOperationId(UUID operationId) {
    this.operationId = operationId;
  }

  @NotNull(message = "Every save names the base it was made against")
  @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
  public UUID getBaseId() {
    return baseId;
  }

  public void setBaseId(UUID baseId) {
    this.baseId = baseId;
  }

  @Valid
  @Schema(description = "Header keys changed against the base")
  public SalesOrderHeaderEdits getHeader() {
    return header;
  }

  public void setHeader(SalesOrderHeaderEdits header) {
    if (header == null) {
      throw new IllegalArgumentException("header must not be null; omit it instead");
    }
    this.header = header;
  }

  @Valid
  @ArraySchema(
      arraySchema = @Schema(description = "Line operations; a line not listed is not touched"))
  public List<SalesOrderLineEdit> getLines() {
    return lines;
  }

  public void setLines(List<SalesOrderLineEdit> lines) {
    this.lines = presentList("lines", lines);
  }

  @Valid
  @ArraySchema(
      arraySchema =
          @Schema(
              description =
                  "One decision per conflict of the base's origin; only on a conflict or expired"
                      + " base"))
  public List<SalesOrderEditResolution> getResolutions() {
    return resolutions;
  }

  public void setResolutions(List<SalesOrderEditResolution> resolutions) {
    this.resolutions = presentList("resolutions", resolutions);
  }

  @Schema(
      format = "uuid",
      description =
          "The edit session (browser tab) whose field leases this save uses (CEDIT-07). Needed"
              + " when leases are enforced and the save changes any key; not part of the save's"
              + " identity: a retry may carry a newer session or tokens under the same operation id.")
  public UUID getEditSessionId() {
    return editSessionId;
  }

  public void setEditSessionId(UUID editSessionId) {
    if (editSessionId == null) {
      throw new IllegalArgumentException("editSessionId must not be null; omit it instead");
    }
    this.editSessionId = editSessionId;
  }

  @Size(max = SalesOrderEditLeaseDtos.MAX_TOKENS)
  @ArraySchema(
      schema = @Schema(format = "uuid"),
      uniqueItems = true,
      maxItems = SalesOrderEditLeaseDtos.MAX_TOKENS,
      arraySchema =
          @Schema(
              description =
                  "One lease token per key this save writes, held by editSessionId; no other"
                      + " token (CEDIT-07). The server computes which keys need a lease."))
  public List<UUID> getLeaseTokens() {
    return leaseTokens;
  }

  public void setLeaseTokens(List<UUID> leaseTokens) {
    List<UUID> tokens = presentList("leaseTokens", leaseTokens);
    if (new java.util.HashSet<>(tokens).size() != tokens.size()) {
      throw new IllegalArgumentException("leaseTokens must be distinct");
    }
    this.leaseTokens = tokens;
  }

  private static <T> List<T> presentList(String name, List<T> values) {
    if (values == null || values.stream().anyMatch(java.util.Objects::isNull)) {
      throw new IllegalArgumentException(name + " must not be or contain null; omit it instead");
    }
    return List.copyOf(values);
  }

  @JsonAnySetter
  public void rejectUnknownProperty(String name, Object value) {
    throw new IllegalArgumentException("Unknown SalesOrderEditRequest property: " + name);
  }
}
