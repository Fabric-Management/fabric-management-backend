package com.fabricmanagement.platform.dbprivilege;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TablePrivilegeComparatorTest {

  @Test
  void reportsSurplusPrivilegeWithRelationRoleAndPrivilege() {
    MeasuredRelation relation =
        measured(
            "production.quality_decision",
            'r',
            privileges(TablePrivilege.SELECT, TablePrivilege.INSERT, TablePrivilege.UPDATE),
            privileges(TablePrivilege.SELECT, TablePrivilege.INSERT, TablePrivilege.DELETE));

    assertThat(
            TablePrivilegeComparator.compare(
                List.of(relation),
                Map.of(relation.relation(), TablePrivilegeClass.APPEND_ONLY_LEDGER)))
        .containsExactly(difference(relation.relation(), "fabric_app", "surplus: UPDATE"));
  }

  @Test
  void reportsMissingPrivilegeWithRelationRoleAndPrivilege() {
    MeasuredRelation relation =
        measured(
            "public.processed_event",
            'r',
            privileges(TablePrivilege.INSERT, TablePrivilege.UPDATE, TablePrivilege.DELETE),
            mutablePrivileges());

    assertThat(TablePrivilegeComparator.compare(List.of(relation), Map.of()))
        .containsExactly(difference(relation.relation(), "fabric_app", "missing: SELECT"));
  }

  @Test
  void reportsGrantOptionEvenWhenTheBasePrivilegeIsExpected() {
    MeasuredPrivileges app =
        new MeasuredPrivileges(
            Set.of(
                TablePrivilege.SELECT,
                TablePrivilege.INSERT,
                TablePrivilege.UPDATE,
                TablePrivilege.DELETE),
            Set.of(TablePrivilege.SELECT));
    MeasuredRelation relation = measured("public.processed_event", 'r', app, mutablePrivileges());

    assertThat(TablePrivilegeComparator.compare(List.of(relation), Map.of()))
        .containsExactly(difference(relation.relation(), "fabric_app", "grant option: SELECT"));
  }

  @Test
  void reportsDeclaredKindMismatch() {
    MeasuredRelation relation =
        measured("public.jobrunr_jobs_stats", 'r', privileges(TablePrivilege.SELECT), privileges());

    assertThat(
            TablePrivilegeComparator.compare(
                List.of(relation),
                Map.of(relation.relation(), TablePrivilegeClass.APP_ONLY_READ_ONLY)))
        .containsExactly(
            difference(relation.relation(), "catalogue", "relkind mismatch: expected v but was r"));
  }

  @Test
  void reportsStaleClassificationEntry() {
    assertThat(
            TablePrivilegeComparator.compare(
                List.of(), Map.of("sales.missing_ledger", TablePrivilegeClass.APPEND_ONLY_LEDGER)))
        .containsExactly(
            difference(
                "sales.missing_ledger",
                "catalogue",
                "stale classification: relation does not exist"));
  }

  @Test
  void unlistedRelationDefaultsToMutable() {
    MeasuredRelation relation =
        measured("sales.new_table", 'r', mutablePrivileges(), mutablePrivileges());

    assertThat(TablePrivilegeComparator.compare(List.of(relation), Map.of())).isEmpty();
  }

  @Test
  void assertionCollectsAndReportsEveryDifference() {
    List<PrivilegeDifference> differences =
        List.of(
            difference("sales.first", "fabric_app", "missing: SELECT"),
            difference("sales.second", "fabric_system", "surplus: DELETE"));

    assertThatThrownBy(() -> TablePrivilegeComparator.assertNoDifferences(differences))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("sales.first | fabric_app | missing: SELECT")
        .hasMessageContaining("sales.second | fabric_system | surplus: DELETE");
  }

  private static MeasuredRelation measured(
      String relation, char relkind, MeasuredPrivileges app, MeasuredPrivileges system) {
    EnumMap<RuntimeDatabaseRole, MeasuredPrivileges> roles =
        new EnumMap<>(RuntimeDatabaseRole.class);
    roles.put(RuntimeDatabaseRole.FABRIC_APP, app);
    roles.put(RuntimeDatabaseRole.FABRIC_SYSTEM, system);
    return new MeasuredRelation(relation, relkind, roles);
  }

  private static MeasuredPrivileges mutablePrivileges() {
    return privileges(
        TablePrivilege.SELECT, TablePrivilege.INSERT, TablePrivilege.UPDATE, TablePrivilege.DELETE);
  }

  private static MeasuredPrivileges privileges(TablePrivilege... privileges) {
    return MeasuredPrivileges.of(Set.of(privileges));
  }

  private static PrivilegeDifference difference(String relation, String role, String delta) {
    return new PrivilegeDifference(relation, role, delta);
  }
}
