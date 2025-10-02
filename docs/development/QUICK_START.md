# ⚡ Quick Start Guide - 15 Dakikada Başla

## 🎯 Hedef

Bu kılavuz ile 15 dakika içinde:

- ✅ Projeyi çalıştırabileceksiniz
- ✅ İlk endpoint'inizi ekleyebileceksiniz
- ✅ Test yazıp çalıştırabileceksiniz

---

## 📦 1. Dakika 0-5: Kurulum

### Ön Gereksinimler

```bash
# Bu komutlar çıktı vermelidir:
java -version      # Java 21+
mvn -version       # Maven 3.9+
docker --version   # Docker 20+
```

### Hızlı Başlangıç

```bash
# Terminal'de kopyala-yapıştır:
git clone <repo-url>
cd fabric-management-backend
./scripts/deploy.sh --all
```

**✅ Başardınız!** Sistem ayağa kalktı: http://localhost:8080

---

## 🚀 2. Dakika 5-10: İlk Endpoint

### Adım 1: Controller Oluştur

```java
// services/user-service/src/main/java/.../api/controller/HelloController.java
package com.fabricmanagement.user.api.controller;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/hello")
public class HelloController {

    @GetMapping
    public String sayHello() {
        return "Merhaba Fabric!";
    }

    @GetMapping("/{name}")
    public String sayHelloTo(@PathVariable String name) {
        return "Merhaba " + name + "!";
    }
}
```

### Adım 2: Test Et

```bash
# Terminal 1: Servisi başlat
cd services/user-service
mvn spring-boot:run

# Terminal 2: Test et
curl http://localhost:8081/api/v1/hello
# Çıktı: Merhaba Fabric!

curl http://localhost:8081/api/v1/hello/Developer
# Çıktı: Merhaba Developer!
```

---

## ✅ 3. Dakika 10-15: Test Yaz

### Unit Test Ekle

```java
// services/user-service/src/test/java/.../HelloControllerTest.java
package com.fabricmanagement.user.api.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(HelloController.class)
class HelloControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldSayHello() throws Exception {
        mockMvc.perform(get("/api/v1/hello"))
            .andExpect(status().isOk())
            .andExpect(content().string("Merhaba Fabric!"));
    }

    @Test
    void shouldSayHelloToName() throws Exception {
        mockMvc.perform(get("/api/v1/hello/Developer"))
            .andExpect(status().isOk())
            .andExpect(content().string("Merhaba Developer!"));
    }
}
```

### Test Çalıştır

```bash
# Tek servis testi
cd services/user-service
mvn test

# Tüm testler
cd ../..
mvn test
```

---

## 🎉 Tebrikler!

15 dakikada:

- ✅ Projeyi kurdunuz
- ✅ Endpoint eklediniz
- ✅ Test yazdınız

## 🔥 Sonraki Adımlar

### 1. CRUD Endpoint Ekle (30 dakika)

```java
@RestController
@RequestMapping("/api/v1/products")
public class ProductController {

    @GetMapping
    public List<ProductDTO> getAll() { }

    @GetMapping("/{id}")
    public ProductDTO getById(@PathVariable UUID id) { }

    @PostMapping
    public ProductDTO create(@RequestBody CreateProductRequest request) { }

    @PutMapping("/{id}")
    public ProductDTO update(@PathVariable UUID id, @RequestBody UpdateProductRequest request) { }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable UUID id) { }
}
```

### 2. Database Entegrasyonu (20 dakika)

```java
@Entity
@Table(name = "products")
public class Product extends BaseEntity {
    private String name;
    private BigDecimal price;
}

@Repository
public interface ProductRepository extends JpaRepository<Product, UUID> {
    List<Product> findByNameContaining(String name);
}
```

### 3. Service Layer (15 dakika)

```java
@Service
@RequiredArgsConstructor
public class ProductService {
    private final ProductRepository repository;
    private final ProductMapper mapper;

    public List<ProductDTO> findAll() {
        return repository.findAll()
            .stream()
            .map(mapper::toDTO)
            .collect(Collectors.toList());
    }
}
```

---

## 📚 Faydalı Komutlar

```bash
# Servisi çalıştır
mvn spring-boot:run

# Hot reload ile çalıştır
mvn spring-boot:run -Dspring-boot.run.fork=false

# Sadece compile
mvn compile

# Skip tests
mvn install -DskipTests

# Specific profile
mvn spring-boot:run -Dspring.profiles.active=local

# Debug mode
mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=5005"
```

---

## 🆘 Sorun mu Yaşıyorsunuz?

### Port kullanımda

```bash
# 8080 portu kullanımda
lsof -i :8080
kill -9 <PID>
```

### Docker sorunları

```bash
# Clean restart
docker-compose down -v
docker-compose up -d
```

### Maven sorunları

```bash
# Clean build
mvn clean install -U
```

### Database bağlantı sorunu

```bash
# PostgreSQL'i kontrol et
docker-compose ps postgres-db
docker-compose logs postgres-db
```

---

## 🎯 Cheat Sheet

| Ne Yapmak İstiyorum? | Komut/Dosya Yolu                                                          |
| -------------------- | ------------------------------------------------------------------------- |
| Yeni Controller      | `services/[servis]/src/main/java/.../api/controller/`                     |
| Yeni Service         | `services/[servis]/src/main/java/.../application/service/`                |
| Yeni Entity          | `services/[servis]/src/main/java/.../domain/entity/`                      |
| Yeni DTO             | `services/[servis]/src/main/java/.../api/dto/`                            |
| Yeni Repository      | `services/[servis]/src/main/java/.../infrastructure/persistence/`         |
| Migration ekle       | `services/[servis]/src/main/resources/db/migration/V[N]__description.sql` |
| Test ekle            | `services/[servis]/src/test/java/.../`                                    |
| Config değiştir      | `services/[servis]/src/main/resources/application.yml`                    |

---

**Yardım:** Slack #fabric-dev | **Güncellenme:** October 2025
