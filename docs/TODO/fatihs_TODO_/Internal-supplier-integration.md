# 🌐 TODO: Internal Tenant Data Integration

## 🎯 Amaç

Uygulamayı kullanan farklı **tenant company**’lerin (örneğin iplikçi, dokumacı, boyahane gibi) birbirleriyle veri paylaşabilmesini ve üretim/maliyet bilgilerinin **güvenli, kontrollü ve senkronize** şekilde transferini sağlamak.

## ⚙️ Kapsam

* Dış sistem entegrasyonu **yoktur**; yalnızca sistemde kayıtlı **internal tenant’lar** arası etkileşim.
* Amaç, farklı tenant’ların birbirlerinin üretim verilerine doğrudan erişmesini değil, **paylaşım yetkisi verilen veri setlerini** (örn. fiyat, teknik detay) görebilmesini sağlamaktır.
* Tenant A, Tenant B’den iplik alıyorsa:

  * Tenant B (iplikçi) kendi Yarn kayıtlarını paylaşılabilir işaretler.
  * Tenant A (dokumacı veya koordinatör) o kayıtları yalnızca **okuma** izniyle görebilir.
* Paylaşım politikaları:

  * Tenant-to-Tenant “trust agreement” tablosu ile yönetilir.
  * Paylaşılan veriler: SKU, teknik detay, fiyat, geçerlilik tarihi, üretim standardı.

## 🧩 Bağımlılıklar

* **fabric-company-service** → Tenant kimlik yönetimi
* **fabric-yarn-service**, **fabric-weaving-service**, **fabric-finishing-service** → Paylaşılacak veri kaynakları
* **fabric-catalog-service** → Tenant bazlı ürün görünümü

## 🚀 Beklenen Çıktılar

* Yetkilendirilmiş veri paylaşımı (örneğin bir iplikçinin onayladığı ürün ve fiyat listesi)
* Tenant bazlı veri paylaşım izinleri yönetimi
* Veri erişimi kayıt altına alınır (audit trail + consent log)

## 🧠 Notlar / Geliştirme Tavsiyesi

* Paylaşım modeli “API key” veya “access grant” tabanlı olmalı.
* CQRS yapısında **read-only projection** kullanılmalı; diğer tenant’ın DB’sine yazma yok.
* Paylaşım politikaları config tablosu üzerinden versiyonlanabilir.
