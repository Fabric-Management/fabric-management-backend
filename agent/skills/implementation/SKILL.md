---
name: backend-implementation
description: Senior Staff Engineer guide for implementing new backend features in Java/Spring Boot, Node.js, and Python projects from scratch. Covers the full implementation lifecycle — domain modeling, service/repository/controller layers, event-driven architecture, and testing. Enforces modern patterns including Lambda/Stream API, Optional chains, CompletableFuture, Pattern Matching (Java 21+), and design patterns (Strategy, Builder, Factory, Observer). Also covers reactive programming (WebFlux, R2DBC), transaction management, and API design. Use when the user asks to implement a new feature, yeni feature yaz, yeni endpoint ekle, sıfırdan implement et, modül oluştur, service yaz, "bu feature'ı nasıl implement ederim", or any request to build new backend functionality. Also triggers on: "yeni bir service oluştur", "bu entity'yi yaz", "event listener ekle", "yeni modül kur", "API endpoint implement et".
---

# Backend Implementation Guide

Yeni backend feature'larını sıfırdan, endüstri standartlarında implement etmek için kapsamlı rehber. Her adımda **neden** o yaklaşımın seçildiği açıklanır ve modern Java/Spring Boot pattern'ları aktif olarak uygulanır.

---

## Ön Adım: Kapsam ve Bağlam Analizi

Kod yazmaya başlamadan önce şunları netleştir:

1. **Bounded Context**: Bu feature hangi modüle ait? Mevcut modüllerle ilişkisi ne?
2. **Aggregate / Entity**: Hangi domain nesneleri oluşturulacak veya değiştirilecek?
3. **Use Case'ler**: Hangi kullanıcı senaryolarını karşılayacak? (Actor → Action → Outcome)
4. **Entegrasyon noktaları**: Başka modüllerle event, API veya shared kernel üzerinden iletişim var mı?
5. **Non-functional requirements**: Performans, concurrency, idempotency, auditability beklentileri

Bu bilgiyi topla ve bir **Implementation Plan** oluştur — sonra koda geç.

---

## Katmanlı Implementasyon Sırası

Önerilen sıra (domain-first yaklaşım):

```
1. Domain Layer    → Entity, Value Object, Enum, Domain Event
2. Repository      → JPA Repository, Custom Query, Specification
3. Service         → Business Logic, Orchestration, Event Publishing
4. DTO / Mapper    → Request/Response DTO, MapStruct / manual mapper
5. Controller      → REST Endpoint, Validation, Error Response
6. Event Listener  → Async handler, Saga, Integration Event
7. Config          → Security, Bean, Properties
8. Test            → Unit, Integration, Slice Test
```

---

## 1. Domain Layer

### Entity Tasarımı

```java
@Entity
@Table(name = "fiber_batches")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // JPA için
public class FiberBatch extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String batchCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BatchStatus status;

    @Version  // Optimistic locking — concurrency safety
    private Long version;

    // ✅ Factory method — constructor yerine anlamlı isimle oluştur
    public static FiberBatch create(String batchCode, FiberType fiberType) {
        var batch = new FiberBatch();
        batch.batchCode = Objects.requireNonNull(batchCode, "batchCode must not be null");
        batch.status = BatchStatus.CREATED;
        batch.registerEvent(new BatchCreatedEvent(batch.batchCode));
        return batch;
    }

    // ✅ Domain logic entity içinde — anemic model'dan kaçın
    public void activate() {
        if (this.status != BatchStatus.CREATED) {
            throw new InvalidBatchStateException(
                "Cannot activate batch in state: " + this.status
            );
        }
        this.status = BatchStatus.ACTIVE;
        this.registerEvent(new BatchActivatedEvent(this.batchCode));
    }
}
```

**Prensipler:**

- Entity, kendi iş kurallarını taşır (Rich Domain Model). Setter'lar yerine anlamlı domain method'ları yaz
- `@Version` ile optimistic locking — concurrent update'lerde veri kaybını önler
- Factory method pattern — `new` yerine `create()` veya `of()` ile oluştur, invariant'ları garanti et
- Domain event'leri entity içinde register et, service katmanında publish et

### Value Object

```java
// ✅ Record — immutable value object için ideal (Java 16+)
public record Weight(BigDecimal value, WeightUnit unit) {

    public Weight {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Weight must be positive");
        }
        Objects.requireNonNull(unit);
    }

    public Weight convertTo(WeightUnit targetUnit) {
        var factor = unit.conversionFactorTo(targetUnit);
        return new Weight(value.multiply(factor), targetUnit);
    }
}
```

### Enum ile State Machine

```java
public enum BatchStatus {
    CREATED {
        @Override
        public Set<BatchStatus> allowedTransitions() {
            return EnumSet.of(ACTIVE, CANCELLED);
        }
    },
    ACTIVE {
        @Override
        public Set<BatchStatus> allowedTransitions() {
            return EnumSet.of(IN_QC, BLOCKED, COMPLETED);
        }
    },
    IN_QC {
        @Override
        public Set<BatchStatus> allowedTransitions() {
            return EnumSet.of(ACTIVE, BLOCKED, COMPLETED);
        }
    },
    BLOCKED {
        @Override
        public Set<BatchStatus> allowedTransitions() {
            return EnumSet.of(ACTIVE, CANCELLED);
        }
    },
    COMPLETED {
        @Override
        public Set<BatchStatus> allowedTransitions() {
            return Set.of(); // terminal state
        }
    },
    CANCELLED {
        @Override
        public Set<BatchStatus> allowedTransitions() {
            return Set.of(); // terminal state
        }
    };

    public abstract Set<BatchStatus> allowedTransitions();

    // ✅ Transition validation — entity'den çağrılır
    public void validateTransitionTo(BatchStatus target) {
        if (!allowedTransitions().contains(target)) {
            throw new InvalidBatchStateException(
                "Cannot transition from %s to %s".formatted(this, target)
            );
        }
    }
}
```

---

## 2. Modern Java Patterns (Lambda, Stream, Optional, CompletableFuture)

### Stream API — Koleksiyon İşlemleri

```java
// ❌ Eski stil — imperative döngü
List<BatchDto> result = new ArrayList<>();
for (FiberBatch batch : batches) {
    if (batch.getStatus() == BatchStatus.ACTIVE) {
        result.add(mapper.toDto(batch));
    }
}

// ✅ Stream API — declarative, okunabilir, chain edilebilir
var activeBatches = batches.stream()
    .filter(batch -> batch.getStatus() == BatchStatus.ACTIVE)
    .map(mapper::toDto) // method reference
    .sorted(Comparator.comparing(BatchDto::createdAt).reversed())
    .toList(); // Java 16+ immutable list
```

**Stream Best Practices:**

- `toList()` (Java 16+) tercih et, `collect(Collectors.toList())` yerine — daha kısa, immutable
- Side-effect içeren `forEach` yerine `map` + `collect` pipeline'ı
- `parallelStream()` sadece CPU-bound, büyük koleksiyonlarda — I/O işlemlerinde kullanma
- `Stream.of()`, `IntStream.range()`, `Stream.generate()` ile stream oluştur

### Stream ile Gruplama ve Aggregation

```java
// Batch'leri status'a göre grupla ve say
Map<BatchStatus, Long> statusCounts = batches.stream()
    .collect(Collectors.groupingBy(
        FiberBatch::getStatus,
        Collectors.counting()
    ));

// Supplier'a göre toplam ağırlık
Map<String, BigDecimal> weightBySupplier = batches.stream()
    .collect(Collectors.groupingBy(
        FiberBatch::getSupplierCode,
        Collectors.reducing(
            BigDecimal.ZERO,
            FiberBatch::getWeightKg,
            BigDecimal::add
        )
    ));

// Partition: certified vs non-certified
Map<Boolean, List<FiberBatch>> partitioned = batches.stream()
    .collect(Collectors.partitioningBy(FiberBatch::isCertified));
```

### Optional — Null Safety

```java
// ❌ Null check zinciri
FiberBatch batch = repository.findById(id);
if (batch == null) {
    throw new NotFoundException("Batch not found");
}
if (batch.getCertification() == null) {
    return "No certification";
}
return batch.getCertification().getStandard();

// ✅ Optional chain — fluent, null-safe
return repository.findById(id)
    .orElseThrow(() -> new ResourceNotFoundException("Batch", id))
    .getCertification()
    .map(Certification::getStandard)
    .orElse("No certification");
```

**Optional Best Practices:**

- `Optional` sadece return type olarak kullan — field veya parametre olarak kullanma
- `isPresent()` + `get()` yerine `map()`, `flatMap()`, `orElseThrow()` chain'i
- `Optional.ofNullable()` — null olabilecek değer; `Optional.of()` — kesinlikle null değilse
- `orElseGet(() -> compute())` tercih et, `orElse(compute())` değil — lazy evaluation

### CompletableFuture — Async Orchestration

```java
@Service
@RequiredArgsConstructor
public class BatchEnrichmentService {

    private final CertificationClient certClient;
    private final QualityClient qualityClient;
    private final PricingClient pricingClient;

    // ✅ Paralel async çağrılar — bağımsız servisler eşzamanlı çalışır
    public CompletableFuture<EnrichedBatch> enrichBatch(String batchCode) {

        var certFuture = CompletableFuture
            .supplyAsync(() -> certClient.getCertification(batchCode));

        var qualityFuture = CompletableFuture
            .supplyAsync(() -> qualityClient.getQualityReport(batchCode));

        var pricingFuture = CompletableFuture
            .supplyAsync(() -> pricingClient.getLatestPrice(batchCode));

        // Üç sonucu birleştir
        return certFuture
            .thenCombine(qualityFuture, (cert, quality) ->
                new PartialEnrichment(cert, quality))
            .thenCombine(pricingFuture, (partial, pricing) ->
                EnrichedBatch.from(batchCode, partial, pricing))
            .exceptionally(ex -> {
                log.error("Enrichment failed for batch: {}", batchCode, ex);
                return EnrichedBatch.fallback(batchCode);
            });
    }
}
```

**CompletableFuture Best Practices:**

- `thenApply` (sync transform), `thenCompose` (async chain — flatMap), `thenCombine` (paralel birleştir)
- `exceptionally` veya `handle` ile hata yönetimi — exception'ı yutma
- Timeout: `.orTimeout(5, TimeUnit.SECONDS)` (Java 9+)
- Custom `Executor` kullan — default ForkJoinPool'u tüketme

### Pattern Matching (Java 21+)

```java
// ✅ Switch expression + pattern matching
public BigDecimal calculateProcessingCost(Material material) {
    return switch (material) {
        case Fiber f when f.isOrganic()   -> f.getWeight().multiply(ORGANIC_RATE);
        case Fiber f                       -> f.getWeight().multiply(STANDARD_RATE);
        case Yarn y                        -> y.getLength().multiply(YARN_RATE);
        case Fabric fab                    -> fab.getArea().multiply(FABRIC_RATE);
        // Sealed class ise default gerekmez — exhaustive check
    };
}

// ✅ Record pattern destructuring (Java 21+)
public String describe(Shape shape) {
    return switch (shape) {
        case Circle(var radius)          -> "Circle with radius " + radius;
        case Rectangle(var w, var h)     -> "Rectangle %dx%d".formatted(w, h);
    };
}
```

---

## 3. Design Patterns — Uygulamalı

### Strategy Pattern — Değişen iş kurallarını izole et

```java
// Sertifika doğrulama stratejisi — yeni standart eklemek mevcut kodu değiştirmez
public interface CertificationValidator {
    boolean supports(CertificationType type);
    ValidationResult validate(BatchCertification cert);
}

@Component
public class GotsCertificationValidator implements CertificationValidator {
    @Override
    public boolean supports(CertificationType type) {
        return type == CertificationType.GOTS;
    }

    @Override
    public ValidationResult validate(BatchCertification cert) {
        // GOTS-specific validation logic
        return cert.getExpiryDate().isAfter(LocalDate.now())
            ? ValidationResult.valid()
            : ValidationResult.expired("GOTS certificate expired");
    }
}

// ✅ Strategy resolver — Spring IoC ile otomatik toplama
@Component
@RequiredArgsConstructor
public class CertificationValidatorResolver {

    private final List<CertificationValidator> validators;

    public CertificationValidator resolve(CertificationType type) {
        return validators.stream()
            .filter(v -> v.supports(type))
            .findFirst()
            .orElseThrow(() -> new UnsupportedCertificationException(type));
    }
}
```

### Builder Pattern — Karmaşık nesne oluşturma

```java
// ✅ Lombok @Builder yerine custom builder — validation ile
public class BatchSearchCriteria {

    private final BatchStatus status;
    private final LocalDate fromDate;
    private final LocalDate toDate;
    private final String supplierCode;
    private final Set<CertificationType> certTypes;

    private BatchSearchCriteria(Builder builder) {
        this.status = builder.status;
        this.fromDate = builder.fromDate;
        this.toDate = builder.toDate;
        this.supplierCode = builder.supplierCode;
        this.certTypes = builder.certTypes != null
            ? Set.copyOf(builder.certTypes)  // immutable copy
            : Set.of();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private BatchStatus status;
        private LocalDate fromDate;
        private LocalDate toDate;
        private String supplierCode;
        private Set<CertificationType> certTypes;

        public Builder status(BatchStatus status) {
            this.status = status;
            return this;
        }

        public Builder dateRange(LocalDate from, LocalDate to) {
            if (from != null && to != null && from.isAfter(to)) {
                throw new IllegalArgumentException("fromDate cannot be after toDate");
            }
            this.fromDate = from;
            this.toDate = to;
            return this;
        }

        // ... diğer setter'lar

        public BatchSearchCriteria build() {
            return new BatchSearchCriteria(this);
        }
    }
}
```

### Factory Pattern — Nesne oluşturma kararını merkezileştir

```java
// ✅ Spring-powered factory — yeni tür eklemek sadece yeni class eklemek
@Component
@RequiredArgsConstructor
public class MaterialProcessorFactory {

    private final Map<MaterialType, MaterialProcessor> processorMap;

    @Autowired
    public MaterialProcessorFactory(List<MaterialProcessor> processors) {
        this.processorMap = processors.stream()
            .collect(Collectors.toMap(
                MaterialProcessor::getSupportedType,
                Function.identity()
            ));
    }

    public MaterialProcessor getProcessor(MaterialType type) {
        return Optional.ofNullable(processorMap.get(type))
            .orElseThrow(() -> new UnsupportedMaterialException(type));
    }
}
```

### Observer / Event-Driven — Domain Event Publishing

```java
// Domain Event
public record BatchCreatedEvent(
    String batchCode,
    LocalDateTime occurredAt
) {
    public BatchCreatedEvent(String batchCode) {
        this(batchCode, LocalDateTime.now());
    }
}

// Publisher — Service katmanında
@Service
@RequiredArgsConstructor
public class BatchService {

    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public FiberBatch createBatch(CreateBatchCommand cmd) {
        var batch = FiberBatch.create(cmd.batchCode(), cmd.fiberType());
        var saved = repository.save(batch);

        // ✅ Transaction commit sonrası publish — veri tutarlılığı
        eventPublisher.publishEvent(new BatchCreatedEvent(saved.getBatchCode()));
        return saved;
    }
}

// Listener — ayrı concern, ayrı class
@Component
@RequiredArgsConstructor
@Slf4j
public class BatchEventListener {

    private final NotificationService notificationService;
    private final TaskGeneratorService taskGenerator;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async
    public void onBatchCreated(BatchCreatedEvent event) {
        log.info("Batch created: {}", event.batchCode());
        taskGenerator.generateTasksForNewBatch(event.batchCode());
        notificationService.notifyStakeholders(event);
    }
}
```

---

## 4. Reactive Patterns (WebFlux / R2DBC)

```java
// ✅ Reactive endpoint — non-blocking I/O
@RestController
@RequestMapping("/api/v1/reactive/batches")
@RequiredArgsConstructor
public class ReactiveBatchController {

    private final ReactiveBatchService batchService;

    @GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<BatchDto> streamActiveBatches() {
        return batchService.streamActiveBatches()
            .map(BatchMapper::toDto)
            .onErrorResume(ex -> {
                log.error("Stream error", ex);
                return Flux.empty();
            });
    }

    @GetMapping("/{id}")
    public Mono<ResponseEntity<BatchDto>> getById(@PathVariable Long id) {
        return batchService.findById(id)
            .map(batch -> ResponseEntity.ok(BatchMapper.toDto(batch)))
            .defaultIfEmpty(ResponseEntity.notFound().build());
    }
}

// Reactive Service
@Service
@RequiredArgsConstructor
public class ReactiveBatchService {

    private final ReactiveBatchRepository repository;
    private final ReactiveCircuitBreakerFactory cbFactory;

    public Mono<FiberBatch> findById(Long id) {
        return repository.findById(id)
            .switchIfEmpty(Mono.error(
                new ResourceNotFoundException("Batch", id)
            ));
    }

    // ✅ External service call with resilience
    public Mono<EnrichedBatch> enrichBatch(Long id) {
        return findById(id)
            .flatMap(batch ->
                Mono.zip(
                    getCertification(batch).onErrorReturn(Certification.empty()),
                    getQualityReport(batch).onErrorReturn(QualityReport.empty())
                ).map(tuple -> EnrichedBatch.of(batch, tuple.getT1(), tuple.getT2()))
            )
            .transformDeferred(CircuitBreakerOperator.of(
                cbFactory.create("enrichment")
            ))
            .timeout(Duration.ofSeconds(5));
    }
}
```

**Ne zaman Reactive kullan:**

- Yüksek concurrency + I/O-bound (binlerce eşzamanlı bağlantı, streaming)
- Aksi halde imperative Spring MVC yeterli ve daha basit

---

## 5. Service Katmanı — Orchestration

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class BatchBlendingService {

    private final BatchRepository batchRepository;
    private final CertificationValidatorResolver certResolver;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public BlendResult blendBatches(BlendCommand cmd) {

        // 1. Input validation — fail fast
        if (cmd.sourceBatchIds().size() < 2) {
            throw new BusinessException("At least 2 batches required for blending");
        }

        // 2. Domain nesnelerini yükle — N+1'den kaçınmak için tek sorgu
        var sourceBatches = batchRepository.findAllByIdWithCertifications(
            cmd.sourceBatchIds()
        );

        // 3. Business rule — tüm batch'ler aktif mi?
        var invalidBatches = sourceBatches.stream()
            .filter(b -> b.getStatus() != BatchStatus.ACTIVE)
            .map(FiberBatch::getBatchCode)
            .toList();

        if (!invalidBatches.isEmpty()) {
            throw new InvalidBatchStateException(
                "Batches not in ACTIVE state: " + String.join(", ", invalidBatches)
            );
        }

        // 4. Certification compatibility check — Strategy pattern
        var certResult = sourceBatches.stream()
            .map(FiberBatch::getCertification)
            .flatMap(Optional::stream) // Optional'ları stream'e dönüştür
            .map(cert -> certResolver.resolve(cert.getType()).validate(cert))
            .reduce(ValidationResult::merge)
            .orElse(ValidationResult.valid());

        if (!certResult.isValid()) {
            throw new CertificationException(certResult.errors());
        }

        // 5. Create blended batch
        var blendedBatch = FiberBatch.createBlended(sourceBatches, cmd.targetBatchCode());
        var saved = batchRepository.save(blendedBatch);

        // 6. Publish event
        eventPublisher.publishEvent(
            new BatchBlendedEvent(saved.getBatchCode(), cmd.sourceBatchIds())
        );

        log.info("Batches blended: {} → {}",
            cmd.sourceBatchIds(), saved.getBatchCode());

        return BlendResult.success(saved.getBatchCode());
    }
}
```

---

## 6. Repository Katmanı

```java
public interface BatchRepository extends JpaRepository<FiberBatch, Long> {

    // ✅ JOIN FETCH — N+1 sorguyu önler
    @Query("""
        SELECT DISTINCT b FROM FiberBatch b
        LEFT JOIN FETCH b.certifications
        WHERE b.id IN :ids
        """)
    List<FiberBatch> findAllByIdWithCertifications(@Param("ids") List<Long> ids);

    // ✅ DTO Projection — gereksiz veri çekme
    @Query("""
        SELECT new com.example.dto.BatchSummaryDto(
            b.id, b.batchCode, b.status, b.createdAt
        )
        FROM FiberBatch b
        WHERE b.status = :status
        """)
    Page<BatchSummaryDto> findSummariesByStatus(
        @Param("status") BatchStatus status,
        Pageable pageable
    );

    // ✅ Specification — dynamic query building
    default Page<FiberBatch> search(BatchSearchCriteria criteria, Pageable pageable) {
        return findAll(BatchSpecifications.fromCriteria(criteria), pageable);
    }
}
```

---

## 7. Controller & DTO Katmanı

```java
@RestController
@RequestMapping("/api/v1/batches")
@RequiredArgsConstructor
@Tag(name = "Batch Management")
@Slf4j
public class BatchController {

    private final BatchService batchService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('PRODUCTION_PLANNER')")
    public ApiResponse<BatchDto> createBatch(
            @Valid @RequestBody CreateBatchRequest request) {

        log.info("Creating batch: {}", request.batchCode());
        var batch = batchService.createBatch(request.toCommand());
        return ApiResponse.created(BatchMapper.toDto(batch));
    }

    @GetMapping
    public ApiResponse<Page<BatchSummaryDto>> listBatches(
            @Valid BatchSearchRequest searchParams,
            @PageableDefault(size = 20, sort = "createdAt", direction = DESC)
            Pageable pageable) {

        var results = batchService.search(searchParams.toCriteria(), pageable);
        return ApiResponse.ok(results);
    }
}

// ✅ Request DTO — validation + immutability
public record CreateBatchRequest(

    @NotBlank(message = "Batch code is required")
    @Pattern(regexp = "^[A-Z]{2}-\\d{6}$", message = "Invalid batch code format")
    String batchCode,

    @NotNull(message = "Fiber type is required")
    FiberType fiberType,

    @Positive(message = "Weight must be positive")
    @NotNull
    BigDecimal weightKg

) {
    public CreateBatchCommand toCommand() {
        return new CreateBatchCommand(batchCode, fiberType, weightKg);
    }
}

// ✅ Consistent API Response envelope
public record ApiResponse<T>(
    boolean success,
    T data,
    String message,
    LocalDateTime timestamp
) {
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null, LocalDateTime.now());
    }

    public static <T> ApiResponse<T> created(T data) {
        return new ApiResponse<>(true, data, "Resource created", LocalDateTime.now());
    }
}
```

---

## 8. Exception Handling — Global

```java
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiError handleNotFound(ResourceNotFoundException ex) {
        return ApiError.of(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(BusinessException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    public ApiError handleBusiness(BusinessException ex) {
        log.warn("Business rule violation: {}", ex.getMessage());
        return ApiError.of(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError handleValidation(MethodArgumentNotValidException ex) {
        var errors = ex.getBindingResult().getFieldErrors().stream()
            .collect(Collectors.toMap(
                FieldError::getField,
                fieldError -> Optional.ofNullable(fieldError.getDefaultMessage())
                    .orElse("Invalid value"),
                (a, b) -> a + "; " + b  // merge duplicate field errors
            ));
        return ApiError.validation(errors);
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiError handleUnexpected(Exception ex) {
        log.error("Unexpected error", ex);
        return ApiError.of(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "An unexpected error occurred"  // detay sızdırma
        );
    }
}
```

---

## 9. Testing Stratejisi

```java
// ✅ Unit Test — domain logic (framework bağımsız, hızlı)
@Test
void should_not_activate_already_active_batch() {
    var batch = FiberBatch.create("FB-000001", FiberType.COTTON);
    batch.activate();

    assertThatThrownBy(batch::activate)
        .isInstanceOf(InvalidBatchStateException.class)
        .hasMessageContaining("Cannot activate");
}

// ✅ Slice Test — repository katmanı
@DataJpaTest
class BatchRepositoryTest {

    @Autowired
    private BatchRepository repository;

    @Test
    void should_fetch_batches_with_certifications_in_single_query() {
        // given: test data
        // when
        var batches = repository.findAllByIdWithCertifications(List.of(1L, 2L));
        // then: assert N+1 yok (Hibernate statistics ile doğrula)
    }
}

// ✅ Integration Test — full flow
@SpringBootTest
@AutoConfigureMockMvc
class BatchControllerIntegrationTest {

    @Autowired private MockMvc mockMvc;

    @Test
    void should_create_batch_and_return_201() throws Exception {
        mockMvc.perform(post("/api/v1/batches")
                .contentType(APPLICATION_JSON)
                .content("""
                    {
                      "batchCode": "FB-000001",
                      "fiberType": "COTTON",
                      "weightKg": 150.5
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.batchCode").value("FB-000001"));
    }
}
```

---

## Kontrol Listesi (Implementation Checklist)

Yeni feature implement ederken her adımda sor:

| Adım          | Kontrol                                                               |
| ------------- | --------------------------------------------------------------------- |
| Domain        | Entity invariant'ları korunuyor mu? Value Object immutable mı?        |
| Stream/Lambda | Imperative döngü declarative'e çevrilebilir mi?                       |
| Optional      | Null dönüş var mı? Optional chain kullanılmalı mı?                    |
| Pattern       | Tekrarlanan if/else → Strategy? Karmaşık oluşturma → Builder?         |
| Repository    | N+1 riski var mı? JOIN FETCH veya DTO projection gerekli mi?          |
| Transaction   | Scope doğru mu? Read-only fırsatı var mı? Event after-commit mi?      |
| Validation    | Input nerede validate ediliyor? DTO constraint'leri yeterli mi?       |
| Error         | Business vs Technical exception ayrımı yapıldı mı?                    |
| Security      | Auth/Authz kontrolü var mı? Input sanitize mi?                        |
| Observability | Log seviyesi doğru mu? Correlation ID var mı?                         |
| Test          | Domain unit test, repository slice test, integration test yazıldı mı? |

---

## Kurallar

1. **Domain-first**: Önce domain modelini ve iş kurallarını yaz, framework'ü sonra ekle.
2. **Modern Java'yı tercih et**: Lambda, Stream, Optional, Record, Pattern Matching — boilerplate'i azaltır, okunabilirliği artırır.
3. **Pattern'ı sorun olduğunda uygula**: Design pattern'ları "olsun diye" değil, gerçek bir karmaşıklığı çözmek için kullan.
4. **Her katmanın sorumluluğu net**: Controller → HTTP, Service → Orchestration, Domain → Business Rules, Repository → Data Access.
5. **İmplementation plan önce, kod sonra**: Kapsam analizi yapmadan koda atılma.
