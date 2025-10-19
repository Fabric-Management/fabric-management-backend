package com.fabricmanagement.fiber.domain.aggregate;

import com.fabricmanagement.fiber.domain.valueobject.*;
import com.fabricmanagement.shared.domain.base.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "fibers")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class Fiber extends BaseEntity {
    
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    
    @Column(name = "code", nullable = false, unique = true, length = 20)
    private String code;
    
    @Column(name = "name", nullable = false, length = 100)
    private String name;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 20)
    private FiberCategory category;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "composition_type", nullable = false, length = 10)
    private CompositionType compositionType;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "origin_type", nullable = false, length = 20)
    private OriginType originType;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "sustainability_type", nullable = false, length = 30)
    private SustainabilityType sustainabilityType;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private FiberStatus status;
    
    @Column(name = "is_default", nullable = false)
    private Boolean isDefault;
    
    @Column(name = "reusable", nullable = false)
    private Boolean reusable;
    
    @Embedded
    private FiberProperty property;
    
    @ElementCollection
    @CollectionTable(name = "fiber_components", joinColumns = @JoinColumn(name = "fiber_id"))
    private List<FiberComponent> components;
}

