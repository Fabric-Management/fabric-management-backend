package com.fabricmanagement.user.domain.aggregate;

import com.fabricmanagement.shared.domain.base.BaseEntity;
import com.fabricmanagement.shared.domain.policy.UserContext;
import com.fabricmanagement.shared.domain.role.SystemRole;
import com.fabricmanagement.user.domain.valueobject.RegistrationType;
import com.fabricmanagement.user.domain.valueobject.UserStatus;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * User Aggregate Root
 * 
 * ⚠️ NO USERNAME FIELD - Authentication uses email/phone from Contact Service
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class User extends BaseEntity {

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    
    @Column(name = "first_name", nullable = false)
    private String firstName;
    
    @Column(name = "last_name", nullable = false)
    private String lastName;
    
    @Column(name = "display_name")
    private String displayName;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private UserStatus status;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "registration_type", nullable = false)
    private RegistrationType registrationType;
    
    @Column(name = "invitation_token")
    private String invitationToken;
    
    @Column(name = "password_hash")
    private String passwordHash;
    
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "role", nullable = false)
    @lombok.Builder.Default
    private SystemRole role = SystemRole.USER;
    
    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;
    
    @Column(name = "last_login_ip")
    private String lastLoginIp;
    
    @Type(JsonBinaryType.class)
    @Column(name = "preferences", columnDefinition = "jsonb")
    private Map<String, Object> preferences;
    
    @Type(JsonBinaryType.class)
    @Column(name = "settings", columnDefinition = "jsonb")
    private Map<String, Object> settings;
    
    @Column(name = "company_id")
    private UUID companyId;
    
    @Column(name = "department_id")
    private UUID departmentId;
    
    @Column(name = "station_id")
    private UUID stationId;
    
    @Column(name = "job_title", length = 100)
    private String jobTitle;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "user_context", nullable = false, length = 50)
    @lombok.Builder.Default
    private UserContext userContext = UserContext.INTERNAL;
    
    @JdbcTypeCode(SqlTypes.ARRAY) // Hibernate 6.x compatibility
    @Column(name = "functions", columnDefinition = "text[]")
    private List<String> functions;
}
