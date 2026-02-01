package com.fabricmanagement.common.platform.company.dto;

import com.fabricmanagement.common.platform.communication.dto.ContactDto;
import com.fabricmanagement.common.platform.company.domain.CompanyContact;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompanyContactDto {

  private UUID companyId;
  private UUID contactId;
  private ContactDto contact;
  private Boolean isDefault;
  private String department;

  public static CompanyContactDto from(CompanyContact companyContact) {
    return CompanyContactDto.builder()
        .companyId(companyContact.getCompanyId())
        .contactId(companyContact.getContactId())
        .contact(
            companyContact.getContact() != null
                ? ContactDto.from(companyContact.getContact())
                : null)
        .isDefault(companyContact.getIsDefault())
        .department(companyContact.getDepartment())
        .build();
  }
}
