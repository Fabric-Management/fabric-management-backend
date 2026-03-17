# Lineage (İzlenebilirlik) Modülü - Gelecek Mimari Planları ve Teknik Borçlar

Bu doküman, Lineage modülünün ilerleyen aşamalarda nasıl ölçeklendirileceği ve diğer sistemlerle nasıl entegre edileceği hakkındaki planları içerir. Uygulama büyüdükçe aşağıdaki maddelerin hayata geçirilmesi kritik önem taşır.

## 1. IWM (Inventory & Warehouse Management) Stok Entegrasyonu

Şu anda `BatchLineageService.create` metodu içerisinde ebeveyn (parent) batch'in stok miktarının yeterli olup olmadığı kontrol edilmektedir, ancak fiili bir stok düşümü (consumption) yapılmamaktadır.

**Yapılması Gerekenler (TODO):**
- Lineage kaydı oluşturulduğunda, fırlatılan `BatchLineageCreatedEvent` dinlenerek veya doğrudan bir Saga/Distributed Transaction aracılığıyla IWM modülüne stok düşüm isteği gönderilmelidir.
- `Batch` entity'si üzerindeki `consumedQuantity` alanının güncellenmesi ve `status` geçişlerinin (örn. `IN_PROGRESS` -> `DEPLETED`) IWM modülü ile senkronize çalışması sağlanmalıdır.
- Stok düşümü başarısız olursa, yaratılan Lineage kaydının geri alınması (Compensating Transaction) gereklidir.

## 2. CQRS ve Read Model (Graph DB) Altyapısı

İzlenebilirlik ağaçları (Trace Backward / Trace Forward) şu anda PostgreSQL üzerinde Recursive CTE (Common Table Expressions) kullanılarak çekilmektedir. Bu yöntem orta ölçekli veriler için oldukça performanslı olsa da, milyonlarca satırlık soyağacı verilerinde ve çok derin ağaçlarda veritabanına aşırı yük bindirebilir.

**Yapılması Gerekenler (TODO):**
- **CQRS Okuma Modeli:** Lineage sorguları (Read) ile kayıt işlemleri (Write) birbirinden ayrılmalıdır.
- **Graph Database Entegrasyonu:** `BatchLineageCreatedEvent` ve `BatchLineageDeletedEvent` olayları dinlenerek, soyağacı verileri Neo4j gibi bir Graph veritabanına asenkron olarak yansıtılmalıdır.
- Kullanıcı arayüzünden gelen `traceBackward` ve `traceForward` istekleri doğrudan Graph veritabanı üzerinden karşılanmalı, böylece çok derin ve karmaşık ilişkisel sorgular milisaniyeler içinde yanıtlanmalıdır.

## 3. Event-Driven Maliyet ve Kalite Entegrasyonu

- **Maliyet (Costing):** Yeni bir lineage eklendiğinde, tüketilen alt bileşenlerin maliyetleri üst bileşene yansıtılmalıdır. `BatchLineageCreatedEvent` dinlenerek maliyet hesaplama modülü tetiklenmelidir.
- **Kalite Kontrol:** Eğer bir parent batch kalite testinden kalırsa veya geri çağrılırsa (recall), bu parent'ın kullanıldığı tüm child batch'lerin otomatik olarak karantinaya alınması (status = ON_HOLD) için Event tabanlı bir akış kurulmalıdır.
