package com.fabricmanagement.platform.dbprivilege;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

final class TablePrivilegeComparator {
  private TablePrivilegeComparator() {}

  static List<PrivilegeDifference> compare(
      List<MeasuredRelation> measured, Map<String, TablePrivilegeClass> declaredClassifications) {
    List<PrivilegeDifference> differences = new ArrayList<>();
    Set<String> measuredNames =
        measured.stream().map(MeasuredRelation::relation).collect(Collectors.toSet());

    measured.forEach(
        relation ->
            compareRelation(
                relation,
                declaredClassifications.getOrDefault(
                    relation.relation(), TablePrivilegeClass.MUTABLE),
                differences));

    declaredClassifications.keySet().stream()
        .filter(relation -> !measuredNames.contains(relation))
        .forEach(
            relation ->
                differences.add(
                    new PrivilegeDifference(
                        relation, "catalogue", "stale classification: relation does not exist")));

    return differences.stream().sorted().toList();
  }

  static void assertNoDifferences(List<PrivilegeDifference> differences) {
    if (differences.isEmpty()) {
      return;
    }
    String details =
        differences.stream().map(PrivilegeDifference::toString).collect(Collectors.joining("\n"));
    throw new AssertionError("Table privilege guard found divergences:\n" + details);
  }

  private static void compareRelation(
      MeasuredRelation relation,
      TablePrivilegeClass declaredClass,
      List<PrivilegeDifference> differences) {
    if (relation.relkind() != declaredClass.expectedRelkind()) {
      differences.add(
          new PrivilegeDifference(
              relation.relation(),
              "catalogue",
              "relkind mismatch: expected "
                  + declaredClass.expectedRelkind()
                  + " but was "
                  + relation.relkind()));
    }

    for (RuntimeDatabaseRole role : RuntimeDatabaseRole.values()) {
      MeasuredPrivileges actual = relation.privileges().get(role);
      if (actual == null) {
        differences.add(
            new PrivilegeDifference(
                relation.relation(), role.databaseName(), "missing measured privilege vector"));
        continue;
      }
      ExpectedPrivileges expected = declaredClass.expected(role);
      addPrivilegeDifferences(
          relation.relation(), role, "missing", expected.granted(), actual.granted(), differences);
      addPrivilegeDifferences(
          relation.relation(), role, "surplus", actual.granted(), expected.granted(), differences);

      actual.grantOptions().stream()
          .sorted()
          .forEach(
              privilege ->
                  differences.add(
                      new PrivilegeDifference(
                          relation.relation(), role.databaseName(), "grant option: " + privilege)));
    }
  }

  private static void addPrivilegeDifferences(
      String relation,
      RuntimeDatabaseRole role,
      String difference,
      Set<TablePrivilege> candidates,
      Set<TablePrivilege> exclusions,
      List<PrivilegeDifference> differences) {
    candidates.stream()
        .filter(privilege -> !exclusions.contains(privilege))
        .sorted()
        .forEach(
            privilege ->
                differences.add(
                    new PrivilegeDifference(
                        relation, role.databaseName(), difference + ": " + privilege)));
  }
}

record MeasuredRelation(
    String relation, char relkind, Map<RuntimeDatabaseRole, MeasuredPrivileges> privileges) {
  MeasuredRelation {
    EnumMap<RuntimeDatabaseRole, MeasuredPrivileges> copy =
        new EnumMap<>(RuntimeDatabaseRole.class);
    copy.putAll(privileges);
    privileges = Map.copyOf(copy);
  }
}

record MeasuredPrivileges(Set<TablePrivilege> granted, Set<TablePrivilege> grantOptions) {
  MeasuredPrivileges {
    granted = Set.copyOf(granted);
    grantOptions = Set.copyOf(grantOptions);
  }

  static MeasuredPrivileges of(Set<TablePrivilege> granted) {
    return new MeasuredPrivileges(granted, Set.of());
  }
}

record PrivilegeDifference(String relation, String role, String delta)
    implements Comparable<PrivilegeDifference> {
  private static final Comparator<PrivilegeDifference> ORDERING =
      Comparator.comparing(PrivilegeDifference::relation)
          .thenComparing(PrivilegeDifference::role)
          .thenComparing(PrivilegeDifference::delta);

  @Override
  public int compareTo(PrivilegeDifference other) {
    return ORDERING.compare(this, other);
  }

  @Override
  public String toString() {
    return relation + " | " + role + " | " + delta;
  }
}
