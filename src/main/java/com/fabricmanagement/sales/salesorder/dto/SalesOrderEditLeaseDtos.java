package com.fabricmanagement.sales.salesorder.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

/**
 * Wire types of sales order field leases (CEDIT-07 §4). A lease token is a secret of the edit
 * session (browser tab) that holds the lease: it travels only in request and response bodies, is
 * returned only to its own session and never appears in a list, a refusal or a URL.
 */
public final class SalesOrderEditLeaseDtos {

  private SalesOrderEditLeaseDtos() {}

  /** Request size bounds; the server's configured bounds are published in the policy too. */
  public static final int MAX_KEYS = 50;

  public static final int MAX_TOKENS = 200;

  @Schema(
      name = "SalesOrderEditLeaseMode",
      enumAsRef = true,
      description =
          "OFF: leases are not enforced for this order's tenant yet; none is granted and a save"
              + " needs none. ENFORCED: every save of a leasable key proves the tab's own lease.")
  public enum Mode {
    OFF,
    ENFORCED
  }

  @Schema(
      name = "SalesOrderEditLeaseRequirementReason",
      enumAsRef = true,
      description =
          "NOT_HELD: this tab does not hold the key now (never acquired, expired, released, its"
              + " token was not sent, or its edit session ended); acquire it again. HELD_BY_ANOTHER:"
              + " another edit session (another person or another tab) holds it or an overlapping"
              + " key; holder says who.")
  public enum RequirementReason {
    NOT_HELD,
    HELD_BY_ANOTHER
  }

  /** Acquire: all of the keys for this tab's edit session, or none. */
  @JsonIgnoreProperties(ignoreUnknown = false)
  @Schema(
      name = "SalesOrderEditLeaseAcquireRequest",
      additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
  public record AcquireRequest(
      @NotNull
          @Schema(
              requiredMode = Schema.RequiredMode.REQUIRED,
              format = "uuid",
              description = "This tab's open edit session (CEDIT-06)")
          UUID editSessionId,
      @NotEmpty
          @Size(max = MAX_KEYS)
          @Valid
          @ArraySchema(
              arraySchema =
                  @Schema(
                      requiredMode = Schema.RequiredMode.REQUIRED,
                      description = "Distinct keys; all are granted or none"),
              minItems = 1,
              maxItems = MAX_KEYS)
          List<@NotNull SalesOrderEditLeaseKey> keys) {

    public AcquireRequest {
      keys = keys == null ? null : List.copyOf(keys);
    }

    @JsonAnySetter
    public void rejectUnknownProperty(String name, Object value) {
      throw new IllegalArgumentException(
          "Unknown SalesOrderEditLeaseAcquireRequest property: " + name);
    }
  }

  /** Renew or release: this tab's own leases, named by their tokens. */
  @JsonIgnoreProperties(ignoreUnknown = false)
  @Schema(
      name = "SalesOrderEditLeaseTokensRequest",
      additionalProperties = Schema.AdditionalPropertiesValue.FALSE,
      description =
          "No duration or expiry is accepted: the server decides every expiry from its own clock.")
  public record TokensRequest(
      @NotNull
          @Schema(
              requiredMode = Schema.RequiredMode.REQUIRED,
              format = "uuid",
              description = "This tab's edit session")
          UUID editSessionId,
      @NotEmpty
          @Size(max = MAX_TOKENS)
          @ArraySchema(
              schema = @Schema(format = "uuid"),
              arraySchema =
                  @Schema(
                      requiredMode = Schema.RequiredMode.REQUIRED,
                      description = "Distinct lease tokens of this tab"),
              minItems = 1,
              maxItems = MAX_TOKENS,
              uniqueItems = true)
          List<@NotNull UUID> leaseTokens) {

    public TokensRequest {
      if (leaseTokens != null) {
        if (leaseTokens.stream().anyMatch(java.util.Objects::isNull)) {
          throw new IllegalArgumentException("leaseTokens must not contain null");
        }
        if (new HashSet<>(leaseTokens).size() != leaseTokens.size()) {
          throw new IllegalArgumentException("leaseTokens must be distinct");
        }
        leaseTokens = List.copyOf(leaseTokens);
      }
    }

    @JsonAnySetter
    public void rejectUnknownProperty(String name, Object value) {
      throw new IllegalArgumentException(
          "Unknown SalesOrderEditLeaseTokensRequest property: " + name);
    }
  }

  /** The server's lease timings and bounds; the client never hard-codes them. */
  @Schema(
      name = "SalesOrderEditLeasePolicy",
      description =
          "Renew a held lease every renewAfterSeconds, but only while the person really works in"
              + " the form (typing, choosing, focusing a field; never an open connection, a"
              + " presence renewal or a stream keepalive). Without such input for"
              + " idleAfterSeconds the client stops renewing and lets its leases expire; it warns"
              + " idleWarningSeconds before. A lease that is not renewed ends leaseSeconds after"
              + " its last renewal, and never outlives its edit session.")
  public record Policy(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Mode mode,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1", example = "90")
          long leaseSeconds,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1", example = "30")
          long renewAfterSeconds,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1", example = "300")
          long idleAfterSeconds,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1", example = "60")
          long idleWarningSeconds,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1", example = "50")
          int maxKeysPerRequest,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1", example = "200")
          int maxLeasesPerSession) {}

  /** One lease of this tab, with its token. */
  @Schema(name = "SalesOrderEditLeaseDto", description = "A lease held by this tab's session.")
  public record Lease(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) SalesOrderEditLeaseField key,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, format = "uuid")
          UUID lineId,
      @Schema(
              requiredMode = Schema.RequiredMode.REQUIRED,
              format = "uuid",
              description =
                  "Secret proof of this holding: send it with renew, release and the save. A new"
                      + " holding of the same key has a new token.")
          UUID leaseToken,
      @Schema(
              requiredMode = Schema.RequiredMode.REQUIRED,
              description = "When this holding began; unchanged by renewals and repeats")
          Instant acquiredAt,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant expiresAt) {}

  /** Somebody's lease as anyone who may read the order sees it: no token. */
  @Schema(
      name = "SalesOrderEditLeaseHolderDto",
      description =
          "A lease held now. Another person's edit session id is never shown; for the caller's own"
              + " leases editSessionId tells this tab (equal to its own) from another tab.")
  public record Holder(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) SalesOrderEditLeaseField key,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, format = "uuid")
          UUID lineId,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, format = "uuid") UUID userId,
      @Schema(
              requiredMode = Schema.RequiredMode.REQUIRED,
              nullable = true,
              description = "The holder's display name; null when the account has none.")
          String displayName,
      @Schema(
              requiredMode = Schema.RequiredMode.REQUIRED,
              description = "Whether the holder is the caller (any of the caller's tabs).")
          boolean mine,
      @Schema(
              requiredMode = Schema.RequiredMode.REQUIRED,
              nullable = true,
              format = "uuid",
              description = "The holding edit session, only for the caller's own leases.")
          UUID editSessionId,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant acquiredAt,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant expiresAt) {}

  /** What acquire granted. */
  @Schema(name = "SalesOrderEditLeaseGrantDto")
  public record Grant(
      @ArraySchema(arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED))
          List<Lease> leases,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Policy policy) {}

  /** What renew kept and what this tab lost. */
  @Schema(name = "SalesOrderEditLeaseRenewalDto")
  public record Renewal(
      @ArraySchema(
              arraySchema =
                  @Schema(
                      requiredMode = Schema.RequiredMode.REQUIRED,
                      description = "Still held, with the new expiry"))
          List<Lease> renewed,
      @ArraySchema(
              schema = @Schema(format = "uuid"),
              arraySchema =
                  @Schema(
                      requiredMode = Schema.RequiredMode.REQUIRED,
                      description =
                          "Tokens this tab no longer holds (ended, taken over, or the order left"
                              + " the draft); acquire the key again before saving it"))
          List<UUID> lostLeaseTokens,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Policy policy) {}

  /** Who holds which keys now. */
  @Schema(name = "SalesOrderEditLeasesDto")
  public record Leases(
      @ArraySchema(
              arraySchema =
                  @Schema(
                      requiredMode = Schema.RequiredMode.REQUIRED,
                      description = "In key order"))
          List<Holder> leases,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Policy policy) {}

  /** A key a save writes but does not hold (409 EDIT_LEASE_REQUIRED). */
  @Schema(name = "SalesOrderEditLeaseRequirement")
  public record Requirement(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) SalesOrderEditLeaseField key,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, format = "uuid")
          UUID lineId,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) RequirementReason reason,
      @Schema(
              requiredMode = Schema.RequiredMode.REQUIRED,
              nullable = true,
              description = "Who is in the way (HELD_BY_ANOTHER); null otherwise")
          Holder holder) {}
}
