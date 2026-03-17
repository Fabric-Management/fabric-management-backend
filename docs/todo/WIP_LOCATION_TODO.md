# Work-In-Progress (WIP) & Location Strategy

Bu doküman, FabricOS sisteminde üretim sahasındaki (Work-In-Progress / Yarı Mamul) malların "sanal kayıp" yaşanmadan nasıl takip edileceğini belirler. Temel paradigma: **"Makine bir Lokasyondur."** Güncel iş listesi ve kurallar bu dosyadadır.

## 1. Temel İlke: Makine = Lokasyon (Machine as a Location)

Klasik sistemlerde mal depodan çıkıp üretime girdiğinde doğrudan "Tüketildi (CONSUMED)" olarak işaretlenir. Bu durum, üretim 3 gün sürüyorsa malın 3 gün boyunca sistemde görünmemesine (sanal kayıp) yol açar.

FabricOS'ta bu sorunu çözmek için üretim makineleri (Örn: Tarak Makinesi, Boya Kazanı, Örme Makinesi) sistemde birer **Lokasyon (WarehouseLocation)** olarak tanımlanmalıdır.

### Lokasyon Tipi (Location Type)
`WarehouseLocationType` enum'ına `MACHINE` (veya `PRODUCTION_LINE`) tipi eklenmelidir.
*   Örnek Hiyerarşi: `Fabrika (WAREHOUSE) -> Eğirme Bölümü (ZONE) -> Tarak Makinesi 1 (MACHINE)`

## 2. Üretim Akışı ve Stok Hareketleri (Production Flow)

Mal üretime girdiğinde ve üretimden çıktığında aşağıdaki stok hareket (InventoryTransaction) akışı izlenmelidir:

### Adım 1: Üretime Besleme (Feeding the Machine)
Mal depodan alınıp makineye yüklendiğinde **TÜKETİLMEZ**, sadece **TRANSFER EDİLİR**.
*   **İşlem:** `TRANSFER_OUT` (Depo Lokasyonundan) -> `TRANSFER_IN` (Makine Lokasyonuna)
*   **Sonuç:** Mal hala sistemdedir, miktarı azalmamıştır, sadece lokasyonu "Tarak Makinesi 1" olarak değişmiştir. Durumu `IN_PROGRESS` olabilir.

### Adım 2: Üretim Sonu (Production Completion / Lineage)
Üretim tamamlanıp yeni bir ürün (Örn: İplik) ortaya çıktığında gerçek tüketim gerçekleşir.
*   **İşlem 1 (Tüketim):** Makinedeki hammadde (Elyaf) için `CONSUMPTION` işlemi yapılır (Miktar düşer).
*   **İşlem 2 (Giriş):** Yeni üretilen ürün (İplik) için `RECEIPT` işlemi yapılır ve bir çıkış lokasyonuna (Örn: Kalite Kontrol Alanı) konur.
*   **İşlem 3 (Soy Ağacı):** Tüketilen hammadde ile yeni üretilen ürün arasında `BatchLineage` (Soy Ağacı) kaydı oluşturulur.

## 3. Geliştirme Kuralları (Development Guidelines)

### TODO

- [ ] **`WarehouseLocationType` enum'ına `MACHINE` (veya `PRODUCTION_LINE`) tipi ekle.**
- [ ] **Consume metodu:** `BatchService.consume()` sadece mal gerçekten yok olduğunda (yeni ürüne dönüştüğünde veya fire olduğunda) çağrılmalıdır. Malı makineye götürmek için `transferBatch()` metodu kullanılmalıdır.
- [ ] **WIP Raporlama:** "Şu an üretimde ne kadar malımız var?" sorusunun cevabı, `location.type == 'MACHINE'` olan tüm lokasyonlardaki aktif Lot'ların (Batch) toplamıdır.
- [ ] **UI/UX:** "Üretime Al" (Start Production) butonu, arka planda malı ilgili makine lokasyonuna transfer etmeli ve statüsünü `IN_PROGRESS` yapmalıdır.
