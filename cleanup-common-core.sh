#!/bin/bash

# Script to clean up common-core module
# Run this script from the fabric-management-backend directory

echo "Starting cleanup of common-core module..."

# Base path
BASE_PATH="common/common-core/src/main/java/com/fabricmanagement/common/core"

# Remove files that should be deleted
echo "Removing unnecessary files..."

# Main application file
rm -f "$BASE_PATH/CommonCoreApplication.java"

# Application layer - mapper
rm -f "$BASE_PATH/application/mapper/CoreMapper.java"

# Application layer - messages
rm -rf "$BASE_PATH/application/messages"

# Application layer - service
rm -f "$BASE_PATH/application/service/CoreApplicationService.java"

# Application layer - request DTOs
rm -f "$BASE_PATH/application/dto/request/BaseRequest.java"

# Domain layer - complete directories
rm -rf "$BASE_PATH/domain/event"
rm -rf "$BASE_PATH/domain/model"
rm -rf "$BASE_PATH/domain/repository"
rm -rf "$BASE_PATH/domain/service"

# Infrastructure layer - config
rm -f "$BASE_PATH/infrastructure/config/SwaggerConfig.java"

# Infrastructure layer - messaging
rm -rf "$BASE_PATH/infrastructure/messaging"

# Infrastructure layer - persistence entity duplicates and corrupted files
rm -f "$BASE_PATH/infrastructure/persistence/entity/BaseEntity.java"
rm -f "$BASE_PATH/infrastructure/persistence/entity/BaseEntity.java.removed"
rm -f "$BASE_PATH/infrastructure/persistence/entity/README.md"
rm -f "$BASE_PATH/infrastructure/persistence/entity/README.md.removed"
rm -f "$BASE_PATH/infrastructure/persistence/entity/REMOVED_BaseEntity.md"
rm -f "$BASE_PATH/infrastructure/persistence/entity/REMOVED_BaseEntity.md.removed"

# Infrastructure layer - persistence other
rm -rf "$BASE_PATH/infrastructure/persistence/repository"
rm -rf "$BASE_PATH/infrastructure/persistence/specification"

# Infrastructure layer - web controller
rm -f "$BASE_PATH/infrastructure/web/controller/CoreController.java"

echo "Cleanup complete!"
echo ""
echo "Files that should remain in common-core:"
echo "- application/dto/ApiResponse.java"
echo "- application/dto/response/BaseResponse.java"
echo "- application/util/ValidationUtil.java"
echo "- domain/exception/CoreDomainException.java"
echo "- domain/exception/NotFoundException.java"
echo "- infrastructure/persistence/BaseEntity.java"
echo "- infrastructure/web/controller/BaseController.java"
echo "- infrastructure/web/exception/GlobalExceptionHandler.java"
echo "- infrastructure/web/exception/ErrorResponse.java"
echo "- resources/application.yml"
echo ""
echo "Running tree command to show final structure..."
tree "$BASE_PATH" -I "*.class|target"