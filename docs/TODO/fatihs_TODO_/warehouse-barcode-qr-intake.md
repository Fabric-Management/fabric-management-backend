# 📦 TODO: Warehouse Barcode / QR Input

## 🎯 Amaç

Depo operasyonlarında ürün (kumaş rulosu, iplik balyası, fiber paketi vb.) girişlerinin manuel veri girişi olmadan **barkod veya QR kod** aracılığıyla otomatik olarak yapılabilmesini sağlamak.

## ⚙️ Kapsam

* Her ürün biriminde (rulo, balya vb.) benzersiz bir barkod veya QR kod bulunur.
* Depocu, ürün kabul ekranında barkod okuyucu veya mobil kamera ile kodu okutur.
* Kod, sistemdeki **ilgili ürün kaydı** (örneğin, inbound draft veya inventory item) ile eşleştirilir.
* Barkod/QR taraması sonucu gelen bilgiler:

  * Ürün tipi (YARN, GREIGE, FINISHED)
  * Ürün ID veya SKU
  * Lot / batch / roll numarası
  * Ölçü (metre, kilogram vb.)
  * Renk / varyant
* Manuel giriş opsiyonu korunur; tarama yalnızca süreci hızlandırır.

## 🧩 Bağımlılıklar

* **fabric-inbound-service** → Ceki listesi / irsaliye / fatura yönetimi
* **fabric-inventory-service** → Depo stok yönetimi
* **fabric-catalog-service** → Ürün kimlikleri (SKU, tip)

## 🚀 Beklenen Çıktılar

* QR/barkod taramasıyla hızlı ürün girişi
* Eşleşmeyen ürünler için otomatik uyarı (örn. yanlış ürün veya eksik kayıt)
* Barkod/QR doğrulama logları (audit trail)
* Mobil / web arayüz desteği

## 🧠 Notlar / Geliştirme Tavsiyesi

* Tarayıcı entegrasyonu için `@zxing-js/library` veya native scanner API'leri kullanılabilir.
* Kod doğrulama işlemleri **asenkron** yapılmalı; UI bloklanmamalı.
* Kod şeması tenant bazlı tanımlanmalı (örneğin, `TENANTCODE-ROLLID-YYYYMMDD`).
