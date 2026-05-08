package com.fabricmanagement.platform.tenant.app;

import com.fabricmanagement.platform.communication.domain.Contact;
import com.fabricmanagement.platform.communication.infra.repository.ContactRepository;
import com.fabricmanagement.platform.organization.domain.Department;
import com.fabricmanagement.platform.organization.domain.Organization;
import com.fabricmanagement.platform.organization.infra.repository.DepartmentRepository;
import com.fabricmanagement.platform.organization.infra.repository.OrganizationRepository;
import com.fabricmanagement.platform.tenant.domain.Tenant;
import com.fabricmanagement.platform.tenant.domain.TenantType;
import com.fabricmanagement.platform.tenant.infra.repository.TenantRepository;
import com.fabricmanagement.platform.user.domain.Role;
import com.fabricmanagement.platform.user.domain.User;
import com.fabricmanagement.platform.user.domain.UserContact;
import com.fabricmanagement.platform.user.domain.UserDepartment;
import com.fabricmanagement.platform.user.infra.repository.RoleRepository;
import com.fabricmanagement.platform.user.infra.repository.UserContactRepository;
import com.fabricmanagement.platform.user.infra.repository.UserRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service responsible for cloning the Golden Template tenant into an isolated Playground tenant.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TenantClonerService {

  private final TenantRepository tenantRepository;
  private final OrganizationRepository organizationRepository;
  private final DepartmentRepository departmentRepository;
  private final RoleRepository roleRepository;
  private final UserRepository userRepository;
  private final ContactRepository contactRepository;
  private final UserContactRepository userContactRepository;

  @Transactional
  public Tenant cloneTemplateToPlayground() {
    log.info("Starting template clone process for new playground tenant.");

    // 1. Find the TEMPLATE tenant
    Tenant templateTenant =
        tenantRepository.findByType(TenantType.TEMPLATE).stream()
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("No TEMPLATE tenant found for cloning."));

    UUID templateTenantId = templateTenant.getId();

    // 2. Create the new PLAYGROUND tenant
    String playgroundSuffix = UUID.randomUUID().toString().substring(0, 8);
    Tenant playgroundTenant =
        Tenant.create(
            "Playground " + playgroundSuffix,
            "PG-" + playgroundSuffix,
            "playground-" + playgroundSuffix,
            null,
            TenantType.PLAYGROUND);
    playgroundTenant = tenantRepository.save(playgroundTenant);
    UUID newTenantId = playgroundTenant.getId();

    log.info("Created new PLAYGROUND tenant: {}", newTenantId);

    // 3. Clone Organization
    Map<UUID, Organization> orgMap = new HashMap<>();
    List<Organization> orgs =
        organizationRepository.findByTenantIdAndIsActiveTrue(templateTenantId);
    for (Organization oldOrg : orgs) {
      Organization newOrg =
          Organization.builder()
              .name(oldOrg.getName())
              .organizationType(oldOrg.getOrganizationType())
              .taxId(oldOrg.getTaxId())
              .description(oldOrg.getDescription())
              .build();
      newOrg.setTenantId(newTenantId);
      newOrg = organizationRepository.save(newOrg);
      orgMap.put(oldOrg.getId(), newOrg);
    }

    // 4. Clone Departments
    Map<UUID, Department> deptMap = new HashMap<>();
    List<Department> depts = departmentRepository.findByTenantIdAndIsActiveTrue(templateTenantId);
    for (Department oldDept : depts) {
      Department newDept =
          Department.builder()
              .organizationId(orgMap.get(oldDept.getOrganizationId()).getId())
              .departmentName(oldDept.getDepartmentName())
              .departmentCode(oldDept.getDepartmentCode())
              .description(oldDept.getDescription())
              .isSystemDepartment(oldDept.getIsSystemDepartment())
              .departmentGroup(oldDept.getDepartmentGroup())
              .displayOrder(oldDept.getDisplayOrder())
              .build();
      newDept.setTenantId(newTenantId);
      newDept = departmentRepository.save(newDept);
      deptMap.put(oldDept.getId(), newDept);
    }

    // 5. Clone Roles
    Map<UUID, Role> roleMap = new HashMap<>();
    List<Role> roles = roleRepository.findByTenantIdAndIsActiveTrue(templateTenantId);
    for (Role oldRole : roles) {
      Role newRole =
          Role.builder()
              .roleName(oldRole.getRoleName())
              .roleCode(oldRole.getRoleCode())
              .description(oldRole.getDescription())
              .roleScope(oldRole.getRoleScope())
              .build();
      newRole.setTenantId(newTenantId);
      newRole = roleRepository.save(newRole);
      roleMap.put(oldRole.getId(), newRole);
    }

    // 6. Clone Users, UserDepartments, and Contacts
    // Two-pass fetch to avoid MultipleBagFetchException:
    List<User> users = userRepository.findByTenantIdWithRelations(templateTenantId);
    userRepository.findByTenantIdWithContacts(templateTenantId); // L1 cache merge
    for (User oldUser : users) {
      User newUser =
          User.builder()
              .organizationId(orgMap.get(oldUser.getOrganizationId()).getId())
              .firstName(oldUser.getFirstName())
              .lastName(oldUser.getLastName())
              .userType(oldUser.getUserType())
              .trustLevel(oldUser.getTrustLevel())
              .build();
      newUser.setTenantId(newTenantId);
      if (oldUser.getRole() != null) {
        Role clonedRole = roleMap.get(oldUser.getRole().getId());
        newUser.setRole(clonedRole != null ? clonedRole : oldUser.getRole());
      }
      newUser = userRepository.save(newUser);

      for (UserDepartment oldUd : oldUser.getUserDepartments()) {
        UserDepartment newUd =
            UserDepartment.builder()
                .userId(newUser.getId())
                .departmentId(deptMap.get(oldUd.getDepartment().getId()).getId())
                .isPrimary(oldUd.getIsPrimary())
                .build();
        newUd.setTenantId(newTenantId);
        newUser.getUserDepartments().add(newUd);
      }

      for (UserContact oldUc : oldUser.getUserContacts()) {
        Contact oldContact = oldUc.getContact();
        Contact newContact =
            Contact.builder()
                .contactValue(oldContact.getContactValue())
                .contactType(oldContact.getContactType())
                .isVerified(oldContact.getIsVerified())
                .label(oldContact.getLabel())
                .isPersonal(oldContact.getIsPersonal())
                .build();
        newContact.setTenantId(newTenantId);
        newContact = contactRepository.save(newContact);

        // UserContact has NO cascade on User — must be saved explicitly via repository
        UserContact newUc =
            UserContact.builder()
                .userId(newUser.getId())
                .contactId(newContact.getId())
                .isDefault(oldUc.getIsDefault())
                .build();
        newUc.setTenantId(newTenantId);
        userContactRepository.save(newUc);
      }

      userRepository.save(newUser);
    }

    log.info("Cloning completed for playground tenant: {}", newTenantId);
    return playgroundTenant;
  }
}
