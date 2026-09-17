package com.fabricmanagement.platform.dbprivilege;

import java.util.Set;

enum TablePrivilegeClass {
  MUTABLE(
      'r',
      ExpectedPrivileges.of(
          TablePrivilege.SELECT,
          TablePrivilege.INSERT,
          TablePrivilege.UPDATE,
          TablePrivilege.DELETE),
      ExpectedPrivileges.of(
          TablePrivilege.SELECT,
          TablePrivilege.INSERT,
          TablePrivilege.UPDATE,
          TablePrivilege.DELETE)),
  APPEND_ONLY_LEDGER(
      'r',
      ExpectedPrivileges.of(TablePrivilege.SELECT, TablePrivilege.INSERT),
      ExpectedPrivileges.of(TablePrivilege.SELECT, TablePrivilege.INSERT, TablePrivilege.DELETE)),
  CLOSE_ONCE_LEDGER(
      'r',
      ExpectedPrivileges.of(TablePrivilege.SELECT, TablePrivilege.INSERT, TablePrivilege.UPDATE),
      ExpectedPrivileges.of(TablePrivilege.SELECT, TablePrivilege.INSERT, TablePrivilege.DELETE)),
  MUTABLE_SYSTEM_PURGE(
      'r',
      ExpectedPrivileges.of(TablePrivilege.SELECT, TablePrivilege.INSERT, TablePrivilege.UPDATE),
      ExpectedPrivileges.of(
          TablePrivilege.SELECT,
          TablePrivilege.INSERT,
          TablePrivilege.UPDATE,
          TablePrivilege.DELETE)),
  READ_ONLY_ARCHIVE(
      'r',
      ExpectedPrivileges.of(TablePrivilege.SELECT),
      ExpectedPrivileges.of(TablePrivilege.SELECT, TablePrivilege.DELETE)),
  NO_RUNTIME_ACCESS('r', ExpectedPrivileges.none(), ExpectedPrivileges.none()),
  APP_ONLY_MUTABLE(
      'r',
      ExpectedPrivileges.of(
          TablePrivilege.SELECT,
          TablePrivilege.INSERT,
          TablePrivilege.UPDATE,
          TablePrivilege.DELETE),
      ExpectedPrivileges.none()),
  APP_ONLY_READ_ONLY('v', ExpectedPrivileges.of(TablePrivilege.SELECT), ExpectedPrivileges.none());

  private final char expectedRelkind;
  private final ExpectedPrivileges appPrivileges;
  private final ExpectedPrivileges systemPrivileges;

  TablePrivilegeClass(
      char expectedRelkind, ExpectedPrivileges appPrivileges, ExpectedPrivileges systemPrivileges) {
    this.expectedRelkind = expectedRelkind;
    this.appPrivileges = appPrivileges;
    this.systemPrivileges = systemPrivileges;
  }

  char expectedRelkind() {
    return expectedRelkind;
  }

  ExpectedPrivileges expected(RuntimeDatabaseRole role) {
    return switch (role) {
      case FABRIC_APP -> appPrivileges;
      case FABRIC_SYSTEM -> systemPrivileges;
    };
  }
}

enum RuntimeDatabaseRole {
  FABRIC_APP("fabric_app"),
  FABRIC_SYSTEM("fabric_system");

  private final String databaseName;

  RuntimeDatabaseRole(String databaseName) {
    this.databaseName = databaseName;
  }

  String databaseName() {
    return databaseName;
  }

  static RuntimeDatabaseRole fromDatabaseName(String databaseName) {
    for (RuntimeDatabaseRole role : values()) {
      if (role.databaseName.equals(databaseName)) {
        return role;
      }
    }
    throw new IllegalArgumentException("Unknown runtime database role: " + databaseName);
  }
}

enum TablePrivilege {
  SELECT,
  INSERT,
  UPDATE,
  DELETE,
  TRUNCATE,
  REFERENCES,
  TRIGGER
}

record ExpectedPrivileges(Set<TablePrivilege> granted, Set<TablePrivilege> grantOptions) {
  ExpectedPrivileges {
    granted = Set.copyOf(granted);
    grantOptions = Set.copyOf(grantOptions);
  }

  static ExpectedPrivileges of(TablePrivilege... privileges) {
    return new ExpectedPrivileges(Set.of(privileges), Set.of());
  }

  static ExpectedPrivileges none() {
    return new ExpectedPrivileges(Set.of(), Set.of());
  }
}
