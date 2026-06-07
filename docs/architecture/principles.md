# Mimari Prensipler

> Bu belge, bu kod tabanında mimari ve teknoloji kararları alırken başvurulacak pusuladır.
> Kurallar değil, **karar verme prensipleridir** — her biri aynı anda her karara uygulanmaz.
>
> **Ana pusula:** Her kararda iki soruyu sor:
> 1. Bu kararın **değiştirme maliyeti** ne kadar? (Yüksekse şimdi doğru kur; düşükse ertele.)
> 2. Bu kuralı **insana mı yoksa makineye mi** emanet ediyorum? (Mümkünse makineye.)
>
> Not: Bu proje büyük ölçüde AI agent'lar tarafından geliştirilir. Bu yüzden "makineyle
> zorlama" prensibi burada normalden ağır basar — makineyle kontrol edilmeyen her kural
> zamanla aşınır.

---

## 1. Kalibrasyon ve "en iyi"nin anlamı

- **Kalibrasyon, maksimalizm değil.** Bir kararın değiştirme maliyeti yüksekse şimdi para
  harca; düşükse sonraya ertele. "Ne bedel olursa öderim" enerjisi yanlış yöne akarsa seni
  fazla-mühendisliğe sürükler.
- **"En iyi" ≠ "maksimum".** Senior mimarinin işareti her şeyi en ağır şekilde yapmak değil,
  doğru yere yatırım yapmaktır.
- **İki tür kötü mimari vardır:** az-mühendislik (her şey birbirine girer) ve fazla-mühendislik
  (basit işi 5 katman + 3 event + 2 port ile yaparsın). İkincisi daha tehlikelidir çünkü
  "kaliteli" görünür.
- **Bedeli bilinçli öde, refleksle değil.** Port/adapter/event törenini her yere dağıtma;
  karmaşıklığı karmaşık domain'lere sakla, trivial CRUD'a değil.
- **Boring tech bilinçli bir kalite kararıdır.** Java 21 / Spring Boot / PostgreSQL / Flyway
  zaten sıkıcı derecede doğru. Yeni teknoloji peşinde koşma; gerçek bir tetikleyici çıkana
  dek "havalı" desenler (Kafka, event-sourcing, CQRS) saf maliyettir.

## 2. Makineyle zorlama (özellikle agent ekibinde)

- **Agent, makineyle kontrol edilmeyen her kuralı eninde sonunda çiğner.** Korkulukları
  wiki'ye değil CI'a/DB'ye göm: ArchUnit, RLS, kontrat testleri, outbox testleri.
- **İnsana güvenme, altyapıya göm.** Tenant izolasyonu için: bir firmanın verisini başkasına
  gösteren tek unutulmuş `WHERE` yeter. Bu yüzden izolasyon DB katmanında (RLS) zorlanır,
  "her geliştirici filtrelemeyi hatırlasın" sözleşmesiyle değil.
- **Agent'lar isimlere göre davranır.** Yanıltıcı bir ad (privileged owner'a "user" demek)
  borçtur; greenfield'da açık isim bedava clarity'dir.
- **Sınırlar tek tek makul istisnalarla erir.** Her istisna ayrı ayrı mantıklı görünür ama
  toplamı sınırı yok eder. İstisna yüzeyini küçük tut; çoğalan istisna sarı ışıktır.
- **Sadelik güvenliktir.** Karmaşıklık (ör. aşırı scope-merge mantığı) bir saldırı yüzeyidir.

> Bu projede makineyle-zorlanan korkuluklar: `RlsPolicyEnforcementIT` (her tenant_id
> tablosunda RLS), `RlsAllowlistArchTest` (RLS-muaf tablo allowlist'i), ArchUnit Article 14
> (`SystemTransactionExecutor` yalnızca whitelist'ten erişilebilir), `DomainEvent` null-tenant
> guard'ı, ConstitutionArchTest (modül sınırları).

## 3. Kanıt kültürü

- **"Compile clean" ≠ "kanıtlandı".** Testin yazılması, koşması demek değildir.
- **"Büyük test yeşil" ≠ "şu özellik kanıtlandı".** Bir test başka sebeplerle de geçebilir;
  deterministik, hedefli kanıt iste (ör. async listener'ın *içinde* `current_setting` oku).
- **Doğrula, varsayma.** Bir şüpheyi dile getirmeden önce koddan kontrol et — bazen şüphe
  haklı çıkar (JobRunr CREATE grant'i), bazen çıkmaz (JsonUnwrapped doğruymuş).
- **Walking skeleton mimariyi kanıtlar, kâğıt değil.** Tek bir dikey dilimi uçtan uca geçir
  (sipariş → üretim → sevkiyat), mimari orada sınavdan geçer.
- **Bir bütün ya gerçek korumadır ya güvenlik tiyatrosudur.** Parçalardan biri eksikse
  (örn. RLS'te app-rolü + FORCE politikalar + fabric_app ile çalışan test) kalanların hepsi
  tiyatrodur.
- **"Pre-existing" ≠ "bizim değişiklik kırmadı".** Bir testin önceden de kırmızı olması, senin
  değişikliğinin onu kırmadığı anlamına gelmez — hata modunun senin dikişine işaret etmesi
  önemlidir. Kırmızı kanarya testini stash'leyip geçme; kök nedenini bul.

## 4. Güvenli varsayılanlar

- **Fail-closed > fail-silent.** Tenant context yoksa sessizce SYSTEM'e düşme — hata fırlat
  (`requireTenantId()`). Sessiz fallback latent bug'ları gizler.
- **Deny-by-default.** Bir şey ters giderse sıfır satır gör, tüm satırlar değil.
- **Keystone'u bul.** Bir önkoşul eksikse üstüne kurulan her şey "çalışır gibi" görünüp
  aslında bypass edilir. RLS'te keystone: uygulamanın hangi DB rolüyle bağlandığıdır —
  owner/superuser ile bağlanırsan RLS sessizce devre dışı kalır ve hiç hata almazsın.

## 5. Sıralama ve borç yönetimi

- **Monolith-first, extract-later.** Mikroservis vergisini erken ödeme; ama sınırları ileride
  ucuza ayrılabilecek şekilde çiz (modül kendi tablosunun sahibi, sınır-ötesi FK/JOIN yok).
- **Değiştirmesi en pahalı olanı önce ve doğru kur:** tenant izolasyonu, tutarlılık modeli,
  authz, API kontratı, gözlemlenebilirlik temeli. Ucuz değişenleri (basit CRUD töreni,
  cache) sonraya bırak.
- **Decoupling kazanırsın, traceability kaybedersin.** Event her yerdeyse iş akışı görünmez
  olur; çok adımlı süreçleri implicit event zinciri yerine explicit saga/process-manager ile
  yönet. Gerçek bounded-context sınırını geçen "olmuş-bitmiş gerçekler" için event kullan.
- **Tutarlılık sözleşmesini yaz.** Modül-içi → senkron/aynı transaction; modüller-arası
  entegrasyon → transactional outbox + idempotent tüketici. Bu seçimi ad-hoc bırakma.
- **Kırmızı bir core test'in üstüne yeni değişiklik yığma.** Her ekleme, sorunun teşhisini
  zorlaştırır. Önce yeşile al, sonra devam et.
- **Reviewer önerisine sağlam gerekçeyle itiraz edebilirsin** — ama gerekçeyi "reddedilen
  alternatif" olarak belgele (ör. PlatformAdminService'in BYPASSRLS yerine JPA +
  `executeInTenantContext` ile kalması: per-tenant scope, RLS aktif, daha güvenli).

---

## Nasıl kullanılır

Yeni bir teknoloji/desen/refactor kararında:

1. **Değiştirme maliyeti yüksek mi?** Evetse §1 + §5 — şimdi doğru kur, kanıtla (§3).
2. **Bir kuralı/sınırı mı koruyorsun?** §2 — makineyle zorla (ArchUnit/test/DB), insana bırakma.
3. **Hata/eksik durumda davranış ne?** §4 — fail-closed + deny-by-default.
4. **"Bitti" mi diyorsun?** §3 — deterministik kanıt koştu mu, yoksa sadece derlendi mi?

Pusula hep aynı: **bu kararın değiştirme maliyeti ne, ve onu insana mı yoksa makineye mi
emanet ediyorum?**
