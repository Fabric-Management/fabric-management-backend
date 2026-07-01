package com.fabricmanagement.platform.auth.app;

import static org.assertj.core.api.Assertions.assertThat;

import com.fabricmanagement.platform.auth.app.LoginIdentityBackfillRunner.AuthUserIdentitySource;
import com.fabricmanagement.platform.auth.app.LoginIdentityBackfillRunner.BackfillStats;
import com.fabricmanagement.platform.auth.app.LoginIdentityBackfillRunner.IdentityBackfillGateway;
import com.fabricmanagement.platform.auth.app.LoginIdentityBackfillRunner.IdentityRow;
import com.fabricmanagement.platform.auth.domain.MfaType;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LoginIdentityBackfillRunnerTest {

  private final LoginIdentityBackfillRunner runner = new LoginIdentityBackfillRunner(null);

  @Test
  void backfillCreatesIdentityAndDefaultMembershipWithCopiedCredentials() {
    InMemoryGateway gateway = new InMemoryGateway();
    AuthUserIdentitySource source =
        source(
            "User@Example.COM",
            "hash-1",
            UUID.fromString("10000000-0000-0000-0000-000000000001"),
            UUID.fromString("20000000-0000-0000-0000-000000000001"));

    BackfillStats stats = runner.backfillRows(List.of(source), gateway);

    assertThat(stats).isEqualTo(new BackfillStats(1, 1, 0, 0, 0));
    assertThat(gateway.identities).containsOnlyKeys("user@example.com");
    IdentityState identity = gateway.identities.get("user@example.com");
    assertThat(identity.passwordHash).isEqualTo("hash-1");
    assertThat(identity.mfaEnabled).isTrue();
    assertThat(identity.primaryMfaType).isEqualTo(MfaType.TOTP);
    assertThat(identity.emailVerified).isTrue();
    assertThat(identity.requiresPasswordReset).isFalse();
    assertThat(gateway.membershipsByUserId.values())
        .singleElement()
        .satisfies(
            membership -> {
              assertThat(membership.loginIdentityId).isEqualTo(identity.id);
              assertThat(membership.tenantId).isEqualTo(source.tenantId());
              assertThat(membership.userId).isEqualTo(source.userId());
              assertThat(membership.defaultMembership).isTrue();
            });
  }

  @Test
  void backfillIsIdempotentWhenMembershipAlreadyExists() {
    InMemoryGateway gateway = new InMemoryGateway();
    AuthUserIdentitySource source =
        source(
            "idempotent@example.com",
            "hash-1",
            UUID.fromString("10000000-0000-0000-0000-000000000002"),
            UUID.fromString("20000000-0000-0000-0000-000000000002"));

    BackfillStats firstRun = runner.backfillRows(List.of(source), gateway);
    BackfillStats secondRun = runner.backfillRows(List.of(source), gateway);

    assertThat(firstRun).isEqualTo(new BackfillStats(1, 1, 0, 0, 0));
    assertThat(secondRun).isEqualTo(new BackfillStats(0, 0, 0, 1, 0));
    assertThat(gateway.identities).hasSize(1);
    assertThat(gateway.membershipsByUserId).hasSize(1);
    assertThat(gateway.identities.get("idempotent@example.com").requiresPasswordReset).isFalse();
  }

  @Test
  void backfillMergesDuplicateEmailAcrossTenantsAndRequiresPasswordReset() {
    InMemoryGateway gateway = new InMemoryGateway();
    AuthUserIdentitySource firstTenant =
        source(
            "owner@example.com",
            "hash-first",
            UUID.fromString("10000000-0000-0000-0000-000000000003"),
            UUID.fromString("20000000-0000-0000-0000-000000000003"));
    AuthUserIdentitySource secondTenant =
        source(
            "OWNER@example.com",
            "hash-second",
            UUID.fromString("10000000-0000-0000-0000-000000000004"),
            UUID.fromString("20000000-0000-0000-0000-000000000004"));

    BackfillStats stats = runner.backfillRows(List.of(firstTenant, secondTenant), gateway);

    assertThat(stats).isEqualTo(new BackfillStats(1, 2, 1, 0, 0));
    assertThat(gateway.identities).hasSize(1);
    IdentityState identity = gateway.identities.get("owner@example.com");
    assertThat(identity.passwordHash).isEqualTo("hash-first");
    assertThat(identity.requiresPasswordReset).isTrue();
    assertThat(gateway.membershipsByUserId.values())
        .extracting(membership -> membership.loginIdentityId)
        .containsExactly(identity.id, identity.id);
    assertThat(gateway.membershipsByUserId.values())
        .extracting(membership -> membership.defaultMembership)
        .containsExactly(true, false);
  }

  @Test
  void backfillSkipsAuthUsersWithoutResolvableEmail() {
    InMemoryGateway gateway = new InMemoryGateway();

    BackfillStats stats =
        runner.backfillRows(
            List.of(
                source(
                    null,
                    "hash-1",
                    UUID.fromString("10000000-0000-0000-0000-000000000005"),
                    UUID.fromString("20000000-0000-0000-0000-000000000005")),
                source(
                    "   ",
                    "hash-2",
                    UUID.fromString("10000000-0000-0000-0000-000000000006"),
                    UUID.fromString("20000000-0000-0000-0000-000000000006"))),
            gateway);

    assertThat(stats).isEqualTo(new BackfillStats(0, 0, 0, 0, 2));
    assertThat(gateway.identities).isEmpty();
    assertThat(gateway.membershipsByUserId).isEmpty();
  }

  private static AuthUserIdentitySource source(
      String email, String passwordHash, UUID tenantId, UUID userId) {
    return new AuthUserIdentitySource(
        UUID.randomUUID(),
        tenantId,
        userId,
        email,
        passwordHash,
        true,
        MfaType.TOTP,
        "secret",
        true,
        true,
        2,
        Instant.parse("2026-07-01T10:15:30Z"));
  }

  private static final class InMemoryGateway implements IdentityBackfillGateway {

    private final Map<String, IdentityState> identities = new LinkedHashMap<>();
    private final Map<UUID, MembershipState> membershipsByUserId = new LinkedHashMap<>();

    @Override
    public Optional<IdentityRow> findIdentityByEmail(String email) {
      return Optional.ofNullable(identities.get(email))
          .map(identity -> new IdentityRow(identity.id, identity.requiresPasswordReset));
    }

    @Override
    public UUID insertIdentity(AuthUserIdentitySource source, String email) {
      UUID id = UUID.randomUUID();
      identities.put(
          email,
          new IdentityState(
              id,
              source.passwordHash(),
              source.mfaEnabled(),
              source.primaryMfaType(),
              source.emailVerified(),
              false));
      return id;
    }

    @Override
    public boolean membershipExistsByUserId(UUID userId) {
      return membershipsByUserId.containsKey(userId);
    }

    @Override
    public boolean insertMembership(
        UUID identityId, AuthUserIdentitySource source, boolean defaultMembership) {
      if (membershipsByUserId.containsKey(source.userId())) {
        return false;
      }

      boolean identityTenantAlreadyLinked =
          membershipsByUserId.values().stream()
              .anyMatch(
                  membership ->
                      membership.loginIdentityId.equals(identityId)
                          && membership.tenantId.equals(source.tenantId()));
      if (identityTenantAlreadyLinked) {
        return false;
      }

      membershipsByUserId.put(
          source.userId(),
          new MembershipState(identityId, source.tenantId(), source.userId(), defaultMembership));
      return true;
    }

    @Override
    public void markRequiresPasswordReset(UUID identityId) {
      identities.values().stream()
          .filter(identity -> identity.id.equals(identityId))
          .findFirst()
          .ifPresent(identity -> identity.requiresPasswordReset = true);
    }
  }

  private static final class IdentityState {
    private final UUID id;
    private final String passwordHash;
    private final boolean mfaEnabled;
    private final MfaType primaryMfaType;
    private final boolean emailVerified;
    private boolean requiresPasswordReset;

    private IdentityState(
        UUID id,
        String passwordHash,
        boolean mfaEnabled,
        MfaType primaryMfaType,
        boolean emailVerified,
        boolean requiresPasswordReset) {
      this.id = id;
      this.passwordHash = passwordHash;
      this.mfaEnabled = mfaEnabled;
      this.primaryMfaType = primaryMfaType;
      this.emailVerified = emailVerified;
      this.requiresPasswordReset = requiresPasswordReset;
    }
  }

  private record MembershipState(
      UUID loginIdentityId, UUID tenantId, UUID userId, boolean defaultMembership) {}
}
