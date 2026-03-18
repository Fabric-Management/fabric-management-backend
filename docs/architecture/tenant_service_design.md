# TenantService ve Multi-Tenant Altyapı Mimarisi

Bu doküman, sistemin çok kiracılı (multi-tenant) mimarisini ve `TenantService` bileşeninin rolünü açıklar.

## 1. Multi-Tenant Yaklaşımı

Sistemimiz **Logical Isolation (Ayrıcalıklı Şema/Sütun)** modelini kullanmaktadır. Tüm tenant'lar aynı veritabanı instance'ını paylaşır, ancak verilere erişim `tenant_id` alanı üzerinden filtreleme yapılarak güvence altına alınır.

- Tüm entity'ler `BaseEntity`den türetilir ve zorunlu bir `tenant_id` alanına sahiptir.
- Hibernate intercepter'ları veya JPA Repository bazlı özel sorgular, API isteklerindeki `Authentication` token'ı üzerinden (`TenantContext`) aktif tenant'ı otomatik filtreler.

## 2. TenantService'in Rolü (K2 Gereksinimi)

Kullanıcılar istek attıklarında isteklerin hepsi tek bir tenant'a spesifiktir. Ancak sistem arka planında çalışan Cron Job'lar (bölüm bazlı analizler, eskalasyonlar, performans değerlendirmeleri vs.) spesifik bir kullanıcının isteğinden bağımsızdır.

Bunun sonucu olarak, örneğin `FlowBoardPerformanceJob` gibi task'lar;
- Ya veritabanındaki her bir tenant verisini "Cross-Tenant" şekilde aynı anda çekmelidir ki bu performans (Memory, N+1, DB lock) sorunlarına yol açar.
- Ya da sistemdeki tüm aktif tenant'ları listelemeli ve iş mantığını `foreach (tenantId in TenantService.findAllActiveTenantIds())` mantığıyla tenant başına ayrı process'lere bölmelidir (Chunking).

**TenantService Şöyle Konumlanmalıdır:**
```java
public interface TenantService {
    /** 
     * Sistemdeki hesabı dondurulmamış ve aktif olan 
     * tüm organizasyonların/tenantların ID listesini döner. 
     */
    List<UUID> findAllActiveTenantIds();
    
    /** İstenilen tenant'ın konfigürasyonlarını döner. */
    TenantConfig getTenantConfig(UUID tenantId);
}
```

Bu kod parçası henüz sisteme eklenmediğinden iş zekası job'larında ve arka plan görevlerinde [K2] şeklinde TODO olarak bırakılmıştır. Altyapı geliştirilirken `TenantRegistry` adında global bir tablo ya da cache ile tenant yönetimine geçilecek ve bu servis oradan beslenecektir.
