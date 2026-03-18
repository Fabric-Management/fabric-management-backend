# Kod Kalitesi ve Otomatik Hata Tespiti

Bu projede kod hatalarını ve stil sorunlarını otomatik tespit eden yapılar zaten mevcut. Bu belge neyin nerede çalıştığını ve nasıl kullanılacağını özetler.

## Özet

| Katman                  | Ne yapıyor?                                                                 | Ne zaman?                   |
| ----------------------- | --------------------------------------------------------------------------- | --------------------------- |
| **Pre-commit**          | Java format kontrolü (`fmt:check`), migration/entity tutarlılık kontrolleri | Her `git commit` öncesi     |
| **Lokal**               | `make code-quality`, `make code-quality-strict`, `make format-check`        | İsteğe bağlı / PR öncesi    |
| **CI (GitHub Actions)** | Format, Checkstyle, SpotBugs, OWASP Dependency Check, testler, JaCoCo       | Her PR/push (main, develop) |

---

## 1. Pre-commit (Commit öncesi)

**Kurulum:** `make setup` veya `./scripts/setup-git-hooks.sh`

**Ne yapılıyor?**

- **Java:** Staged `.java` dosyası varsa `mvn fmt:check` çalışır. Format uyumsuzluğu varsa commit engellenir.
- **Migration/Entity:** Staged migration veya entity varsa uyarılar + basit SQL kontrolleri (örn. `IF NOT EXISTS`).

**Format hatası alırsan:**

```bash
make format    # veya  mvn fmt:format
git add -A && git commit ...
```

**Atlamak (önerilmez):** `git commit --no-verify`

---

## 2. Lokal komutlar (Makefile)

| Komut                      | Açıklama                                                                             |
| -------------------------- | ------------------------------------------------------------------------------------ |
| `make format`              | Google Java Format ile kodu formatlar                                                |
| `make format-check`        | Sadece format kontrolü (değişiklik yapmaz). Pre-commit bunu kullanır.                |
| `make checkstyle`          | Checkstyle (Google Style). Rapor: `target/checkstyle-result.xml`                     |
| `make spotbugs`            | SpotBugs (bug + güvenlik). Rapor: `target/spotbugsXml.xml`                           |
| `make code-quality`        | format → checkstyle → spotbugs. Hata bulsa bile **build kırmaz** (rapor üretir).     |
| `make code-quality-strict` | Aynı kontroller; **herhangi bir ihlal build'i kırar**. CI'da "geçmeli" senaryo için. |
| `make lint`                | `mvn verify -DskipTests` (test yok, verify fazı çalışır)                             |
| `make test`                | Unit testler                                                                         |
| `make coverage`            | JaCoCo raporu: `target/site/jacoco/index.html`                                       |

**Öneri:** PR açmadan önce `make code-quality-strict` çalıştırıp geçtiğinden emin ol.

---

## 3. CI (GitHub Actions)

**Workflow:** `.github/workflows/ci.yml`

**Job'lar:**

1. **Code Quality Checks**
   - `mvn fmt:format`
   - `mvn checkstyle:check` (artifacts'a yüklenir)
   - `mvn spotbugs:check` (artifacts'a yüklenir)
   - OWASP Dependency Check (güvenlik)
   - Checkstyle/SpotBugs/OWASP şu an `continue-on-error: true` → **build'i kırmaz**, sadece rapor üretir.

2. **Tests**
   - `mvn clean test`
   - Surefire raporu + JaCoCo coverage artifacts'a yüklenir.

**Strict mod (kalite ihlallerinde build'i kırmak):**  
Önce `make code-quality-strict` lokal olarak geçmeli. Sonra CI'da ilgili adımların `continue-on-error` değerini `false` yapıp `code-quality-strict` ile hizalayabilirsin.

---

## 4. Kullanılan araçlar

| Araç                       | Amaç                                   |
| -------------------------- | -------------------------------------- |
| **Google Java Format**     | Kod formatı                            |
| **Checkstyle**             | Google Style uyumu (stil, naming, vb.) |
| **SpotBugs**               | Potansiyel bug tespiti                 |
| **FindSecBugs**            | Güvenlik (SpotBugs eklentisi)          |
| **OWASP Dependency Check** | Bağımlılık güvenlik açıkları           |
| **JaCoCo**                 | Test coverage                          |
| **ArchUnit**               | Mimari kurallar (testlerde)            |

**Checkstyle suppressions:** `checkstyle-suppressions.xml` ile Javadoc (MissingJavadoc*, SummaryJavadoc, JavadocParagraph vb.), stil (LineLength, AvoidStarImport, Indentation, NeedBraces, MissingSwitchDefault) ve isimlendirme (AbbreviationAsWordInName, LocalVariableName) uyarıları proje genelinde kapatılmıştır. Böylece `make checkstyle` temiz geçer; gerçek hata/ güvenlik tespiti SpotBugs/FindSecBugs ve testlerle yapılır. Yeni kodda bu kuralları uygulamak isteğe bağlıdır.

**SpotBugs exclude:** `spotbugs-exclude.xml` ile FindSecBugs SpringEntityLeakDetector'ın generic dönüş tiplerinde (örn. `Map<String,Object>`) çöktüğü sınıflar (`HealthController`, `DevelopmentToolsController`) analiz dışı bırakılmıştır; log temiz kalır, build aynı şekilde geçer.

**Makefile:** `MVN` değişkeni kullanılır; proje kökünde `mvnw` varsa `./mvnw`, yoksa sistemdeki `mvn` çalıştırılır. Maven Wrapper oluşturmak için (bir kez): `mvn -N wrapper:wrapper`.

---

## 5. Hızlı kontrol akışı

```text
git add ...
git commit -m "..."     → Pre-commit: format check (Java) + migration/entity uyarıları
make code-quality       → Lokal: format + checkstyle + spotbugs (raporlar)
make code-quality-strict → Lokal: aynı; ihlal varsa fail
make test && make coverage → Test + coverage
```

PR → CI (format, checkstyle, spotbugs, OWASP, testler, coverage) çalışır; raporlar artifacts'ta.

---

## 6. Yeni bir "otomatik tespit" eklemek

- **Yeni Maven plugin** (örn. Error Prone, PMD): `pom.xml`'e ekle, isteğe bağlı bir Make hedefi ve CI adımı tanımla.
- **Pre-commit'e yeni kural:** `scripts/hooks/pre-commit` içine ekle; hızlı ve deterministik tut (örn. sadece format/stil).
- **Mimari kurallar:** ArchUnit testleriyle genişlet.

---

**Son güncelleme:** 2026-01-31
