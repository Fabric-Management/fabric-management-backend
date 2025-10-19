# 🧵 Fiber Service

**Port:** 8094  
**Base Path:** `/api/v1/fibers`  
**Status:** 🔴 Foundation Service - Textile Chain

---

## 🚀 QUICK START

### 1️⃣ Test Çalıştırma (ŞU AN BU ADIMDASIN!)

```bash
# ADIM 1: Fiber service klasörüne git
cd services/fiber-service

# ADIM 2: Testleri çalıştır (HEPSI FAIL edecek - normal!)
mvn test

# Beklenen sonuç:
# ❌ BUILD FAILURE (çünkü implementation yok!)
# Bu NORMAL! TDD approach - önce test, sonra kod!
```

### 2️⃣ Sadece Build Kontrolü (Test Olmadan)

```bash
# Compile et (test çalıştırma)
mvn clean compile -DskipTests

# Beklenen: SUCCESS ✅
```

### 3️⃣ Specific Test Çalıştırma

```bash
# Sadece FiberServiceTest
mvn test -Dtest=FiberServiceTest

# Sadece Integration tests
mvn test -Dtest=*IT

# Sadece E2E tests
mvn test -Dtest=*E2ETest

# Tek bir test metodu
mvn test -Dtest=FiberServiceTest#shouldCreatePureFiber_whenValidRequest
```

---

## 📋 TDD WORKFLOW (Şimdi İzleyeceğimiz Adımlar)

```
┌─────────────────────────────────────────────────────────┐
│  1. ✅ Tests yazıldı (TAMAMLANDI!)                      │
│                                                         │
│  2. ⏳ mvn test → HEPSI FAIL (Implementation yok)       │
│                                                         │
│  3. ⏳ Domain entities yaz → Bazı testler pass           │
│                                                         │
│  4. ⏳ Repository yaz → Daha fazla test pass             │
│                                                         │
│  5. ⏳ Service yaz → Çoğu test pass                     │
│                                                         │
│  6. ⏳ Controller yaz → Tüm testler pass                │
│                                                         │
│  7. ✅ mvn test → ALL GREEN! 🎉                         │
│                                                         │
│  8. ✅ mvn jacoco:report → Coverage check (≥80%)        │
└─────────────────────────────────────────────────────────┘
```

---

## 🎯 ŞU AN NEREDEYIZ?

```
Durum: Test Suite Hazır ✅
Sırada: Domain Layer Implementation
```

**İlk çalıştırma komutu:**

```bash
# Fiber service klasöründeyken
cd /Users/user/Coding/fabric-management/fabric-management-backend/services/fiber-service

# Testleri çalıştır
mvn test
```

**Beklenen çıktı:**

```
[ERROR] Failed to execute goal...
[ERROR] class not found: Fiber
[ERROR] class not found: FiberService
[ERROR] BUILD FAILURE

Bu NORMAL! 😊
Çünkü henüz implementation yazmadık!
```

---

## 💡 NEDEN TDD?

```
Geleneksel Yöntem:
1. Kod yaz
2. Test yaz
3. Test fail eder → Kod değiştir
4. Tekrar tekrar...
5. Sonunda çalışır ama spaghetti code

TDD (Google/Netflix Yöntemi):
1. Test yaz (Ne istediğini BİL)
2. Test fail eder (Henüz kod yok - RED)
3. Minimal kod yaz (Test geçsin - GREEN)
4. Refactor (Clean up - REFACTOR)
5. Sonuç: Clean, tested, production-ready! ✅
```

---

## 🔥 KOMUTLAR ÖZET

```bash
# ═══════════════════════════════════════════════════
# TEMEL KOMUTLAR
# ═══════════════════════════════════════════════════

# Tüm testleri çalıştır
mvn test

# Test + Coverage report
mvn clean test jacoco:report

# Coverage report'u aç (HTML)
open target/site/jacoco/index.html

# ═══════════════════════════════════════════════════
# HIZLI TESTLER
# ═══════════════════════════════════════════════════

# Sadece unit tests (hızlı - 5 saniye)
mvn test -Dtest=*Test

# Sadece integration tests (orta - 10 saniye)
mvn test -Dtest=*IT

# Sadece E2E tests (yavaş - 30 saniye)
mvn test -Dtest=*E2ETest

# ═══════════════════════════════════════════════════
# SPECIFIC TESTLER
# ═══════════════════════════════════════════════════

# Sadece FiberServiceTest
mvn test -Dtest=FiberServiceTest

# Sadece FiberValidationTest
mvn test -Dtest=FiberValidationTest

# Tek metod
mvn test -Dtest=FiberServiceTest#shouldCreatePureFiber_whenValidRequest

# ═══════════════════════════════════════════════════
# BUILD KOMUTLARI
# ═══════════════════════════════════════════════════

# Test olmadan compile
mvn clean compile -DskipTests

# Tam build (test + package)
mvn clean package

# Integration test dahil
mvn clean verify
```

---

## 📝 SIRA SENDE!

**Şimdi çalıştır:**

```bash
cd services/fiber-service
mvn test
```

**Sonuç:** BUILD FAILURE (expected!)  
**Sebep:** Implementation yok  
**Sıradaki:** Domain entities yaz

**Hazır mısın?** 💪
