# 👤 User vs Employee - Architecture Separation

**Version:** 1.0  
**Last Updated:** 2025-01-27  
**Purpose:** Clear separation between User (authentication) and Employee (HR data)

---

## 🎯 **CRITICAL SEPARATION**

### **User Entity** (`common/platform/user`)
**Purpose:** Authentication, basic identity, platform access

**Fields:**
- `firstName`, `lastName`, `displayName`
- `companyId` (required)
- `role` (authorization)
- `departments` (via UserDepartment junction)
- `contacts`, `addresses` (via Communication module)
- `lastActiveAt`, `onboardingCompletedAt`

**Responsibilities:**
- ✅ Platform authentication
- ✅ Authorization (roles, permissions)
- ✅ Basic profile (name, company, departments)
- ✅ Communication (contacts, addresses)

---

### **Employee Entity** (`human/employee`)
**Purpose:** HR/İK data, personal information, employment details

**Fields:**
- `userId` (One-to-One with User)
- `title` (MR, MISS, MRS, MS, DR, PROF, ENG, NONE)
- `gender` (MALE, FEMALE, OTHER, PREFER_NOT_TO_SAY)
- `birthDate` (for age calculations)
- `nationality` (ISO country code: TR, US, GB, etc.)
- `employeeNumber` (company-specific)
- `hireDate`, `terminationDate`
- `emergencyContact` (embedded value object)

**Responsibilities:**
- ✅ HR/İK data management
- ✅ Personal information (title, gender, birth date)
- ✅ Employment details (hire date, employee number)
- ✅ Emergency contact information

---

## 🔗 **Relationship**

**One-to-One (Optional):**
- Each `User` can have one `Employee` record
- `Employee.userId` → `User.id` (unique constraint)
- Employee record is optional (not all users need HR data)

**Example:**
```java
User user = User.create("John", "Doe", companyId);
// User created for platform access

Employee employee = Employee.builder()
    .userId(user.getId())
    .title(Title.MR)
    .gender(Gender.MALE)
    .birthDate(LocalDate.of(1990, 1, 1))
    .nationality("US")
    .employeeNumber("EMP-001")
    .hireDate(LocalDate.now())
    .build();
// Employee record for HR data
```

---

## 🌍 **Global Support**

### **Title Enum**
- `MR` - Mister
- `MISS` - Unmarried female
- `MRS` - Married female
- `MS` - Female (marital status neutral)
- `DR` - Doctor
- `PROF` - Professor
- `ENG` - Engineer
- `NONE` - No title (default)

### **Gender Enum**
- `MALE`
- `FEMALE`
- `OTHER`
- `PREFER_NOT_TO_SAY`

### **Nationality**
- ISO 3166-1 alpha-2 country codes
- Examples: `TR`, `US`, `GB`, `DE`, `FR`, `CN`, `IN`

---

## 📋 **Usage Guidelines**

### **When to Use User:**
- ✅ Platform authentication
- ✅ Authorization checks
- ✅ Basic profile management
- ✅ Communication (email, phone, address)

### **When to Use Employee:**
- ✅ HR/İK data management
- ✅ Payroll processing
- ✅ Leave management
- ✅ Employee reporting
- ✅ Age calculations
- ✅ Formal communications (with title)

---

## ✅ **Summary**

**User = Authentication Layer**
- Platform access
- Basic identity
- Authorization

**Employee = HR Layer**
- Personal information
- Employment details
- HR management

**Separation Benefits:**
- ✅ Clean architecture
- ✅ Optional HR data (not all users need it)
- ✅ Global support (title, gender, nationality)
- ✅ Compliance with HR regulations

