package com.fabricmanagement.company.application.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import com.fabricmanagement.company.domain.valueobject.Phone;

/**
 * MapStruct mapper for Phone value object conversions.
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface PhoneMapper {

    /**
     * Maps Phone value object to a simple string representation.
     */
    default String phoneToString(Phone phone) {
        if (phone == null) {
            return null;
        }
        return phone.toString();
    }

    /**
     * Maps string to Phone value object.
     */
    default Phone stringToPhone(String phoneString) {
        if (phoneString == null || phoneString.trim().isEmpty()) {
            return null;
        }
        // This would need to be implemented based on Phone constructor
        // For now, returning null to avoid compilation errors
        return null;
    }
}
