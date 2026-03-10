# Backend DTO'ları – TypeScript Tipleri İçin Referans

Backend Java DTO'larının alan listesi. TypeScript interface/type oluştururken kullanın.

**Tip eşlemesi:**
- `UUID` → `string`
- `Instant` → `string` (ISO-8601)
- `LocalDate` → `string` (YYYY-MM-DD)
- `Boolean` / `boolean` → `boolean`
- `Integer` / `Long` / `Double` → `number`
- `JsonNode` → `Record<string, unknown>` veya uygun obje tipi
- Java enum → TypeScript union: `'VALUE1' | 'VALUE2'`

---

## Auth DTOs

### LoginRequest
| Alan | Java Tipi | Not |
|------|-----------|-----|
| contactValue | String | @NotBlank |
| password | String | @NotBlank |

### LoginResponse
| Alan | Java Tipi | Not |
|------|-----------|-----|
| accessToken | String | |
| refreshToken | String | |
| expiresIn | Long | |
| user | UserDto | |
| needsOnboarding | Boolean | @JsonProperty, default false |
| onboardingPrefill | OnboardingPrefillDto \| null | @JsonProperty |

### RefreshTokenRequest
| Alan | Java Tipi |
|------|-----------|
| refreshToken | String |

### PasswordResetRequest
| Alan | Java Tipi | Not |
|------|-----------|-----|
| authUserId | UUID | |
| contactType | String | "EMAIL" \| "PHONE" |

### PasswordResetVerifyRequest
| Alan | Java Tipi | Not |
|------|-----------|-----|
| authUserId | UUID | |
| code | String | 6 digit |
| newPassword | String | min 8 |

### PasswordSetupRequest
| Alan | Java Tipi | Not |
|------|-----------|-----|
| token | String | |
| verificationCode | String? | @Deprecated |
| password | String | min 8 |

### RegisterCheckRequest
| Alan | Java Tipi |
|------|-----------|
| contactValue | String |

### SelfSignupRequest
| Alan | Java Tipi | Not |
|------|-----------|-----|
| organizationName | String | |
| taxId | String | |
| organizationType | OrganizationType | enum |
| firstName | String | |
| lastName | String | |
| email | String | @Email |
| selectedOS | List\<String\>? | |
| acceptedTerms | Boolean | default false |

### VerifyAndRegisterRequest
| Alan | Java Tipi | Not |
|------|-----------|-----|
| contactValue | String | |
| code | String | 6 digit |
| password | String | min 8 |

### LogoutRequest
| Alan | Java Tipi |
|------|-----------|
| refreshToken | String |

### TenantOnboardingRequest
| Alan | Java Tipi | Not |
|------|-----------|-----|
| organizationName | String | |
| taxId | String | |
| organizationType | OrganizationType | |
| address | String? | |
| city | String? | |
| country | String? | |
| phoneNumber | String? | |
| organizationEmail | String? | @Email |
| adminFirstName | String | |
| adminLastName | String | |
| adminContact | String | @Email |
| adminDepartment | String? | |
| selectedOS | List\<String\>? | |
| trialDays | Integer | default 90 |

### TenantOnboardingResponse
| Alan | Java Tipi |
|------|-----------|
| organizationId | UUID |
| tenantId | UUID |
| organizationUid | String |
| organizationName | String |
| adminUserId | UUID |
| adminContactValue | String |
| registrationToken | String |
| subscriptions | List\<String\> |
| trialEndsAt | Instant? |
| setupUrl | String? |

### OnboardingPrefillDto
| Alan | Java Tipi | @JsonProperty |
|------|-----------|----------------|
| primaryEmail | String? | "primaryEmail" |
| companyName | String? | "companyName" |
| taxId | String? | "taxId" |
| companyType | String? | "companyType" |

### MaskedContactInfo
| Alan | Java Tipi |
|------|-----------|
| authUserId | UUID |
| maskedValue | String |
| type | String | "EMAIL" \| "PHONE" |
| verified | Boolean |

### UserContactInfoResponse
| Alan | Java Tipi |
|------|-----------|
| contacts | List\<MaskedContactInfo\> |

---

## User DTOs

### UserDto
| Alan | Java Tipi |
|------|-----------|
| id | UUID |
| tenantId | UUID |
| uid | String |
| firstName | String |
| lastName | String |
| displayName | String? |
| organizationId | UUID? |
| roleId | UUID? |
| role | String? | role name |
| isActive | Boolean? |
| lastActiveAt | Instant? |
| onboardingCompletedAt | Instant? |
| hasCompletedOnboarding | Boolean? |
| createdAt | Instant? |
| updatedAt | Instant? |

### OnboardingStatusResponse
| Alan | Java Tipi |
|------|-----------|
| hasCompletedOnboarding | Boolean |
| completedAt | Instant? |

### UserAddressDto
| Alan | Java Tipi |
|------|-----------|
| uid | String |
| userId | UUID |
| addressId | UUID |
| address | AddressDto? |
| isPrimary | Boolean? |
| isWorkAddress | Boolean? |

### UserContactDto
| Alan | Java Tipi |
|------|-----------|
| uid | String |
| userId | UUID |
| contactId | UUID |
| contact | ContactDto? |
| isDefault | Boolean? |

### UpdateUserRequest
| Alan | Java Tipi |
|------|-----------|
| firstName | String |
| lastName | String |
| department | String? |

### UpdateUserProfileRequest
| Alan | Java Tipi | Not |
|------|-----------|-----|
| firstName | String? | WORK_PROFILE |
| lastName | String? | WORK_PROFILE |
| workEmail | String? | @Deprecated |
| workPhone | String? | @Deprecated |
| workAddress | AddressData? | nested |
| departmentId | UUID? | WORK_PROFILE |
| homeAddress | AddressData? | nested |
| personalPhone | String? | @Deprecated |
| birthDate | LocalDate? | PERSONAL_PROFILE |
| emergencyContact | EmergencyContactData? | nested |

**UpdateUserProfileRequest.AddressData**
| Alan | Java Tipi |
|------|-----------|
| streetAddress | String? |
| city | String? |
| state | String? |
| postalCode | String? |
| country | String? |
| placeId | String? |

**UpdateUserProfileRequest.EmergencyContactData**
| Alan | Java Tipi |
|------|-----------|
| name | String? |
| phone | String? |
| relationship | String? |

### CreateInternalUserRequest
| Alan | Java Tipi | Not |
|------|-----------|-----|
| firstName | String | |
| lastName | String | |
| contactValue | String | |
| contactType | ContactType (user) | EMAIL \| PHONE |
| organizationId | UUID | |
| department | String? | |
| departmentId | UUID? | |
| departmentCategoryId | UUID? | |
| roleId | UUID? | |
| positionId | UUID? | |
| additionalContacts | List\<ContactData\> | default [] |
| addresses | List\<AddressData\> | default [] |
| title | Title? | enum (Mr, Miss, …) |
| gender | Gender? | enum |
| birthDate | LocalDate? | |
| nationality | String? | |
| employeeNumber | String? | |
| hireDate | LocalDate? | |
| emergencyContact | EmergencyContactData? | nested |

**CreateInternalUserRequest.EmergencyContactData**
| Alan | Java Tipi |
|------|-----------|
| name | String? |
| phone | String? |
| relationship | String? |

### CreateExternalUserRequest
| Alan | Java Tipi |
|------|-----------|
| firstName | String |
| lastName | String |
| contactValue | String |
| contactType | ContactType (user) | EMAIL \| PHONE |
| organizationId | UUID |
| department | String? |
| additionalContacts | List\<ContactData\> |
| addresses | List\<AddressData\> |

### CreateAdminUserRequest
| Alan | Java Tipi |
|------|-----------|
| organizationId | UUID |
| tenantId | UUID |
| firstName | String |
| lastName | String |
| contactValue | String |
| department | String? |

### AddressData (user dto – create user)
| Alan | Java Tipi | Not |
|------|-----------|-----|
| streetAddress | String | |
| city | String | |
| state | String? | |
| postalCode | String? | |
| country | String | |
| placeId | String? | |
| addressType | String? | default "WORK" |
| label | String? | |
| isPrimary | Boolean | default false |

### ContactData (user dto)
| Alan | Java Tipi | Not |
|------|-----------|-----|
| contactValue | String | |
| contactType | ContactType (user) | EMAIL \| PHONE |
| label | String? | |
| isPersonal | Boolean | default true |
| isWhatsApp | Boolean? | |
| phoneType | String? | "MOBILE" \| "LANDLINE" |

### RoleDto
| Alan | Java Tipi |
|------|-----------|
| id | UUID |
| tenantId | UUID |
| uid | String |
| roleName | String |
| roleCode | String |
| description | String? |
| isActive | Boolean? |

### CreateRoleRequest
| Alan | Java Tipi |
|------|-----------|
| roleName | String |
| roleCode | String |
| description | String? |

### AssignDepartmentRequest
| Alan | Java Tipi |
|------|-----------|
| departmentId | UUID |
| isPrimary | Boolean | default false |

### UserDepartmentDto
| Alan | Java Tipi |
|------|-----------|
| userId | UUID |
| departmentId | UUID |
| isPrimary | Boolean? |
| assignedAt | Instant? |
| assignedBy | UUID? |

### ProfileUpdateRequestDto
| Alan | Java Tipi |
|------|-----------|
| id | UUID |
| userId | UUID |
| profileCategory | String? | WORK_PROFILE \| PERSONAL_PROFILE |
| status | ProfileUpdateRequestStatus |
| requestedChanges | String? |
| reason | String? |
| reviewedBy | UUID? |
| reviewComment | String? |
| reviewedAt | Instant? |
| createdAt | Instant? |
| updatedAt | Instant? |

### ReviewProfileUpdateRequestDto
| Alan | Java Tipi |
|------|-----------|
| reviewComment | String |

### CreateProfileUpdateRequestDto
| Alan | Java Tipi |
|------|-----------|
| profileCategory | ProfileCategory | WORK_PROFILE \| PERSONAL_PROFILE |
| requestedChanges | JsonNode? | JSON object |
| reason | String? |

---

## Company DTOs

### CompanyDto
| Alan | Java Tipi |
|------|-----------|
| id | UUID |
| tenantId | UUID |
| uid | String |
| companyName | String |
| taxId | String |
| companyType | CompanyType |
| parentCompanyId | UUID? |
| isActive | Boolean? |
| isTenant | Boolean? |
| createdAt | Instant? |
| updatedAt | Instant? |

### CompanyAddressDto
| Alan | Java Tipi |
|------|-----------|
| organizationId | UUID |
| addressId | UUID |
| address | AddressDto? |
| isPrimary | Boolean? |
| isHeadquarters | Boolean? |

### CompanyContactDto
| Alan | Java Tipi |
|------|-----------|
| organizationId | UUID |
| contactId | UUID |
| contact | ContactDto? |
| isDefault | Boolean? |
| department | String? |

### CreateCompanyRequest
| Alan | Java Tipi |
|------|-----------|
| companyName | String |
| taxId | String |
| companyType | CompanyType |
| parentCompanyId | UUID? |

### UpdateCompanyRequest
| Alan | Java Tipi |
|------|-----------|
| companyName | String |
| taxId | String |
| parentCompanyId | UUID? |

### CreateCompanyWithContactRequest
| Alan | Java Tipi | Not |
|------|-----------|-----|
| companyName | String | |
| taxId | String | |
| companyType | CompanyType | |
| parentCompanyId | UUID? | |
| contacts | List\<ContactRequest\> | default [] |
| addresses | List\<AddressRequest\> | default [] |
| email | String? | @Deprecated |
| phoneNumber | String? | @Deprecated |
| address | String? | @Deprecated |
| city | String? | @Deprecated |
| state | String? | @Deprecated |
| postalCode | String? | @Deprecated |
| country | String? | @Deprecated |

### ContactRequest (company – nested)
| Alan | Java Tipi |
|------|-----------|
| contactValue | String |
| contactType | ContactType (communication) |
| isDefault | Boolean? |
| department | String? |

### AddressRequest (company – nested)
| Alan | Java Tipi |
|------|-----------|
| streetAddress | String? |
| city | String? |
| state | String? |
| postalCode | String? |
| country | String? |
| addressType | AddressType |
| isPrimary | Boolean? |
| contactPhone | String? |
| contactEmail | String? |
| contactPerson | String? |

### CreateTenantCompanyRequest
| Alan | Java Tipi |
|------|-----------|
| companyName | String |
| taxId | String |
| companyType | CompanyType |

### CreateInitialSubscriptionsResult
| Alan | Java Tipi |
|------|-----------|
| osCodes | List\<String\> |
| trialEndsAt | Instant? |

### UserCreationOptionsDto
| Alan | Java Tipi |
|------|-----------|
| roles | List\<RoleDto\> |
| departmentCategories | List\<DepartmentCategoryDto\> |
| departments | List\<DepartmentDto\> |
| positions | List\<PositionDto\> |

### CompanyTypeDto
| Alan | Java Tipi |
|------|-----------|
| value | String | enum name |
| label | String | display |
| description | String? |
| category | String | TENANT, SUPPLIER, … |
| isTenant | boolean |
| suggestedOS | String[]? |

### DepartmentDto
| Alan | Java Tipi |
|------|-----------|
| id | UUID |
| tenantId | UUID |
| uid | String |
| organizationId | UUID |
| departmentName | String |
| departmentCode | String? |
| description | String? |
| managerId | UUID? |
| departmentCategoryId | UUID? |
| departmentCategoryName | String? |
| parentDepartmentId | UUID? |
| parentDepartmentName | String? |
| isSystemDepartment | Boolean? |
| displayOrder | Integer? |
| isActive | Boolean? |

### DepartmentCategoryDto
| Alan | Java Tipi |
|------|-----------|
| id | UUID |
| tenantId | UUID |
| uid | String |
| categoryName | String |
| description | String? |
| displayOrder | Integer? |
| isActive | Boolean? |

### PositionDto
| Alan | Java Tipi |
|------|-----------|
| id | UUID |
| tenantId | UUID |
| uid | String |
| departmentId | UUID? |
| departmentName | String? |
| positionName | String |
| positionCode | String? |
| description | String? |
| defaultRoleId | UUID? |
| defaultRoleName | String? |
| hierarchicalParentId | UUID? |
| hierarchicalParentName | String? |
| displayOrder | Integer? |
| isActive | Boolean? |

### SubscriptionDto
| Alan | Java Tipi |
|------|-----------|
| id | UUID |
| tenantId | UUID |
| uid | String |
| osCode | String |
| osName | String |
| status | SubscriptionStatus |
| startDate | Instant? |
| expiryDate | Instant? |
| trialEndsAt | Instant? |
| features | Map\<String, Boolean\>? |
| pricingTier | String? |
| isActive | Boolean? |
| createdAt | Instant? |

### SubscriptionQuotaDto
| Alan | Java Tipi |
|------|-----------|
| id | UUID |
| tenantId | UUID |
| subscriptionId | UUID |
| quotaType | String |
| quotaLimit | Long? |
| quotaUsed | Long? |
| remaining | Long? |
| usagePercentage | Double? |
| resetPeriod | String? |
| lastResetAt | Instant? |
| createdAt | Instant? |

### UpdateSubscriptionRequest
| Alan | Java Tipi |
|------|-----------|
| expiryDate | Instant? |
| features | Map\<String, Boolean\>? |
| pricingTier | String? |

---

## Communication DTOs

### ContactDto
| Alan | Java Tipi |
|------|-----------|
| id | UUID |
| tenantId | UUID |
| uid | String |
| contactValue | String |
| contactType | ContactType (communication) |
| isVerified | Boolean? |
| isPrimary | Boolean? |
| label | String? |
| parentContactId | UUID? |
| isPersonal | Boolean? |
| isActive | Boolean? |
| createdAt | Instant? |
| updatedAt | Instant? |

### AddressDto
| Alan | Java Tipi |
|------|-----------|
| id | UUID |
| tenantId | UUID |
| uid | String |
| streetAddress | String? |
| city | String? |
| state | String? |
| district | String? |
| postalCode | String? |
| country | String? |
| countryCode | String? |
| addressType | AddressType |
| isPrimary | Boolean? |
| label | String? |
| contactPhone | String? |
| contactEmail | String? |
| contactPerson | String? |
| formattedAddress | String? |
| placeId | String? |
| latitude | Double? |
| longitude | Double? |
| isActive | Boolean? |
| createdAt | Instant? |
| updatedAt | Instant? |

### CreateContactRequest
| Alan | Java Tipi |
|------|-----------|
| contactValue | String |
| contactType | ContactType? | infer if null |
| label | String? |
| isPersonal | Boolean | default true |
| parentContactId | UUID? |

### CreateAddressRequest
| Alan | Java Tipi |
|------|-----------|
| streetAddress | String |
| city | String |
| state | String? |
| postalCode | String? |
| country | String |
| addressType | AddressType |
| label | String? |

### AutocompleteRequest
| Alan | Java Tipi |
|------|-----------|
| input | String |
| country | String? |

### AutocompleteResponse
| Alan | Java Tipi |
|------|-----------|
| predictions | List\<AutocompletePrediction\> |

**AutocompleteResponse.AutocompletePrediction**
| Alan | Java Tipi |
|------|-----------|
| placeId | String |
| description | String |
| mainText | String? |
| secondaryText | String? |

### ContactSuggestionsDto
| Alan | Java Tipi |
|------|-----------|
| phoneSuggestion | PhoneSuggestion? |
| emailSuggestions | List\<String\> |

### PhoneSuggestion
| Alan | Java Tipi |
|------|-----------|
| value | String |
| source | String |
| label | String |

### AssignContactRequest
| Alan | Java Tipi |
|------|-----------|
| contactId | UUID |
| isDefault | Boolean | default false |
| department | String? |

### AssignAddressRequest
| Alan | Java Tipi |
|------|-----------|
| addressId | UUID |
| isPrimary | Boolean | default false |
| isWorkAddress | Boolean? | user only |
| isHeadquarters | Boolean? | company only |

### ValidateAddressRequest
| Alan | Java Tipi |
|------|-----------|
| placeId | String? |
| address | String? |
| originalInput | String? |
| addressType | String? |
| label | String? |

### AddressValidationResponse
| Alan | Java Tipi |
|------|-----------|
| verificationStatus | VerificationStatus | VERIFIED \| PARTIAL \| FAILED |
| address | AddressDto? |
| placeId | String? |
| formattedAddress | String? |
| streetAddress | String? |
| flatNumber | String? |
| city | String? |
| state | String? |
| district | String? |
| postalCode | String? |
| country | String? |
| countryCode | String? |
| latitude | Double? |
| longitude | Double? |
| errorMessage | String? |

### WhatsAppCapabilityDto
| Alan | Java Tipi |
|------|-----------|
| phoneNumber | String |
| hasWhatsApp | Boolean |
| canReceiveMessages | Boolean? |

---

## Admin DTOs

### TenantStatistics
| Alan | Java Tipi |
|------|-----------|
| tenantId | UUID |
| tenantUid | String |
| companyName | String |
| userCount | Long |
| companyCount | Long |
| subscriptionCount | Long |
| isActive | Boolean? |

---

## Enums (TypeScript union veya string literal)

### CompanyType
`'SPINNER' | 'WEAVER' | 'KNITTER' | 'DYER_FINISHER' | 'VERTICAL_MILL' | 'GARMENT_MANUFACTURER' | 'FIBER_SUPPLIER' | 'YARN_SUPPLIER' | 'CHEMICAL_SUPPLIER' | 'CONSUMABLE_SUPPLIER' | 'PACKAGING_SUPPLIER' | 'MACHINE_SUPPLIER' | 'LOGISTICS_PROVIDER' | 'MAINTENANCE_SERVICE' | 'IT_SERVICE_PROVIDER' | 'KITCHEN_SUPPLIER' | 'HR_SERVICE_PROVIDER' | 'LAB' | 'UTILITY_PROVIDER' | 'FASON' | 'AGENT' | 'TRADER' | 'FINANCE_PARTNER' | 'CUSTOMER'`

### ContactType (communication)
`'EMAIL' | 'MOBILE' | 'LANDLINE' | 'PHONE_EXTENSION' | 'FAX' | 'WEBSITE' | 'SOCIAL_MEDIA'`

### ContactType (user – auth/create user)
`'EMAIL' | 'PHONE'`

### AddressType
`'HOME' | 'BILLING' | 'MAILING' | 'TEMPORARY' | 'ALTERNATE' | 'OFFICE' | 'HEADQUARTERS' | 'BRANCH' | 'WAREHOUSE' | 'FACTORY' | 'SHIPPING' | 'WORKSITE' | 'REMOTE'`

### SubscriptionStatus
`'TRIAL' | 'ACTIVE' | 'EXPIRED' | 'CANCELLED' | 'SUSPENDED'`

### ProfileCategory
`'WORK_PROFILE' | 'PERSONAL_PROFILE'`

### ProfileUpdateRequestStatus
`'PENDING' | 'APPROVED' | 'REJECTED'`

### VerificationStatus (AddressValidationResponse)
`'VERIFIED' | 'PARTIAL' | 'FAILED'`

---

## Bağımlılık sırası (TypeScript için)

1. Enums / union tipleri
2. Communication: ContactDto, AddressDto, ContactType, AddressType
3. User: AddressData, ContactData, RoleDto → UserDto, UserAddressDto, UserContactDto, …
4. Auth: OnboardingPrefillDto, LoginResponse (UserDto, OnboardingPrefillDto kullanır)
5. Company: CompanyDto, CompanyAddressDto, CompanyContactDto, DepartmentDto, DepartmentCategoryDto, PositionDto, SubscriptionDto, …
6. Admin: TenantStatistics

Bu sırayla import edersen circular dependency riski azalır.
