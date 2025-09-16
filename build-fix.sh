#!/bin/bash

# Script to build common modules and all services after fixing compilation errors
# This script addresses the dependency resolution and compilation errors

echo "=== Fabric Management System Build Fix ==="
echo "Fixing dependency resolution and compilation errors"
echo ""

cd /Users/user/Coding/fabric-management/fabric-management-backend

echo "Step 1: Building common-core module..."
cd common/common-core
mvn clean install
if [ $? -ne 0 ]; then
    echo "ERROR: Failed to build common-core"
    exit 1
fi
echo "✅ common-core built successfully"
echo ""

echo "Step 2: Building common-security module..."
cd ../common-security
mvn clean install
if [ $? -ne 0 ]; then
    echo "ERROR: Failed to build common-security"
    exit 1
fi
echo "✅ common-security built successfully"
echo ""

echo "Step 3: Building user-service..."
cd ../../services/user-service
mvn clean compile
if [ $? -ne 0 ]; then
    echo "ERROR: Failed to compile user-service"
    exit 1
fi
echo "✅ user-service compiled successfully"
echo ""

echo "Step 4: Building contact-service..."
cd ../contact-service
mvn clean compile
if [ $? -ne 0 ]; then
    echo "ERROR: Failed to compile contact-service"
    exit 1
fi
echo "✅ contact-service compiled successfully"
echo ""

echo "Step 5: Building company-service..."
cd ../company-service
mvn clean compile
if [ $? -ne 0 ]; then
    echo "ERROR: Failed to compile company-service"
    exit 1
fi
echo "✅ company-service compiled successfully"
echo ""

echo "Step 6: Building auth-service..."
cd ../auth-service
mvn clean compile
if [ $? -ne 0 ]; then
    echo "ERROR: Failed to compile auth-service"
    exit 1
fi
echo "✅ auth-service compiled successfully"
echo ""

echo "🎉 All 5 services built successfully!"
echo ""
echo "Summary of fixes applied:"
echo ""
echo "Valueobject Enum Fixes:"
echo "- Fixed UserStatus.java with proper enum values"
echo "- Fixed ContactStatus.java with proper enum values"
echo "- Fixed ContactType.java with proper enum values"
echo "- Fixed CompanyType.java with proper enum values"
echo "- Fixed Industry.java with proper enum values"
echo "- Fixed EmailType.java with proper enum values"
echo "- Fixed PhoneType.java with proper enum values"
echo "- Fixed AddressType.java with proper enum values"
echo ""
echo "Company-Service Compilation Fixes:"
echo "- Fixed DuplicateCompanyException.java (proper exception class)"
echo "- Fixed PhoneMapper.java (MapStruct mapper interface)"
echo "- Fixed GlobalExceptionHandler.java (@RestControllerAdvice class)"
echo "- Fixed CompanyDetailResponse.java (comprehensive DTO record)"
echo ""
echo "✅ All valueobject files now contain valid Java enums!"
echo "✅ All company-service classes now contain valid Java implementations!"
echo "✅ All 5 services (user, contact, company, auth, common modules) compile successfully!"
