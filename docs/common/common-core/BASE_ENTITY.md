# BaseEntity

## 📋 Overview

BaseEntity, tüm entity'ler için ortak alanları ve işlevleri sağlayan temel sınıftır. Audit trail, soft delete ve optimistic locking özelliklerini içerir.

## 🏗️ Implementation

```java
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public abstract class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @CreatedBy
    @Column(name = "created_by", updatable = false, length = 100)
    private String createdBy;

    @LastModifiedBy
    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    @Version
    @Column(name = "version")
    private Long version;

    @Column(name = "deleted", nullable = false)
    private Boolean deleted = Boolean.FALSE;

    // Soft delete methods
    public void markAsDeleted() {
        this.deleted = Boolean.TRUE;
    }

    public void restore() {
        this.deleted = Boolean.FALSE;
    }

    public boolean isDeleted() {
        return Boolean.TRUE.equals(this.deleted);
    }

    public boolean isNew() {
        return this.id == null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BaseEntity that = (BaseEntity) o;

        if (id == null || that.id == null) {
            return false;
        }

        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return String.format("%s{id=%s, version=%d, deleted=%s}",
            getClass().getSimpleName(), id, version, deleted);
    }
}
```

## 🎯 Features

### **1. Audit Trail**

- **createdAt**: Oluşturulma tarihi
- **updatedAt**: Güncellenme tarihi
- **createdBy**: Oluşturan kullanıcı
- **updatedBy**: Güncelleyen kullanıcı

### **2. Soft Delete**

- **deleted**: Silinme durumu
- **markAsDeleted()**: Soft delete işlemi
- **restore()**: Geri alma işlemi
- **isDeleted()**: Silinme kontrolü

### **3. Optimistic Locking**

- **version**: Versiyon kontrolü
- **@Version**: JPA optimistic locking

### **4. UUID Primary Key**

- **id**: UUID primary key
- **Distributed system support**: Dağıtık sistem desteği
- **Security**: Güvenlik avantajı

## 🔧 Usage

### **Entity Development**

```java
@Entity
@Table(name = "users")
public class User extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "first_name")
    private String firstName;

    @Column(name = "last_name")
    private String lastName;

    @Enumerated(EnumType.STRING)
    private UserStatus status;

    // BaseEntity'den gelen alanlar otomatik:
    // id, createdAt, updatedAt, createdBy, updatedBy, version, deleted
}
```

### **Repository Development**

```java
@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    // Active users only
    @Query("SELECT u FROM User u WHERE u.deleted = false")
    List<User> findAllActive();

    @Query("SELECT u FROM User u WHERE u.deleted = false AND u.id = :id")
    Optional<User> findActiveById(@Param("id") UUID id);

    @Query("SELECT u FROM User u WHERE u.deleted = false AND u.status = :status")
    List<User> findByStatusAndDeletedFalse(@Param("status") UserStatus status);
}
```

### **Service Development**

```java
@Service
@Transactional
public class UserService {

    public void deleteUser(UUID userId) {
        User user = userRepository.findActiveById(userId)
            .orElseThrow(() -> new EntityNotFoundException("User not found"));

        // Soft delete using BaseEntity method
        user.markAsDeleted();
        userRepository.save(user);
    }

    public void restoreUser(UUID userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new EntityNotFoundException("User not found"));

        user.restore();
        userRepository.save(user);
    }
}
```

## 🧪 Testing Benefits

### **Easy Mocking**

```java
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    @Test
    void shouldDeleteUser() {
        // Given
        UUID userId = UUID.randomUUID();
        User user = User.builder().id(userId).build();
        when(userRepository.findActiveById(userId)).thenReturn(Optional.of(user));

        // When
        userService.deleteUser(userId);

        // Then
        assertThat(user.isDeleted()).isTrue();
        verify(userRepository).save(user);
    }
}
```

## 🎯 Benefits

1. **Audit Trail**: Otomatik audit trail
2. **Soft Delete**: Güvenli silme işlemi
3. **Optimistic Locking**: Concurrency control
4. **UUID Support**: Dağıtık sistem desteği
5. **Code Consistency**: Tutarlı entity yapısı
6. **Easy Testing**: Kolay test edilebilirlik
