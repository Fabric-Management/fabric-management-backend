# Product-Led Growth (PLG): "Playground / Simulation" Kurgusu

**Durum:** Fikir / Tasarım Aşamasında (Uygulamaya başlanmadı)  
**Amacı:** Kullanıcıların üye olma bariyerine takılmadan, uygulamanın değerini, yeteneklerini ve departmanlar arası veri akışını bizzat deneyimleyerek anlamalarını sağlamak.

---

## 1. Temel Kavramlar (Core Concepts)

*   **Geçici Kapsül (Ephemeral Tenant):** Her ziyaretçiye özel, diğerlerinden tamamen izole edilmiş, uygulamanın tam fonksiyonlu ama geçici bir kopyasıdır.
*   **Altın Şablon (Golden Template):** İçerisinde hazır departmanların, rollerin, örnek kumaş/iplik tanımlarının ve birkaç örnek siparişin bulunduğu "kusursuz" başlangıç veritabanı durumudur.
*   **Rol Simülasyonu (Impersonation):** Kullanıcının tek tıklamayla "Satış Yöneticisi" şapkasını çıkarıp "Üretim Planlamacı" şapkasını takabilmesi ve o rolün yetkileriyle kendi yarattığı veriler üzerinde işlem yapabilmesidir.
*   **Dönüşüm (Conversion):** Anonim kullanıcının, denediği ve içine veri girdiği bu sistemi kaybetmek istemeyip resmi olarak "Kayıtlı Müşteri"ye (Tenant) dönüşme anıdır.

---

## 2. Sistemin Yapı Taşları (Building Blocks)

1.  **Kimlik ve Oturum Yöneticisi (Session & Auth Engine)**
    *   Tarayıcıda benzersiz bir `guest_id` oluşturulur.
    *   Kullanıcı rol değiştirdiğinde, arka plan o role uygun "Geçici Playground Yetki Belgesi (JWT)" üretir. Frontend bu belge ile normal bir session gibi çalışır.
2.  **Klonlama Motoru (Tenant Orchestrator)**
    *   Yeni biri simülasyona girdiğinde "Altın Şablon" kopyalanır ve saniyeler içinde o kullanıcı için izole bir "Geçici Kapsül (Playground Tenant)" yaratılır.
3.  **Koruma ve Sınırlandırma Kalkanları (Safeguards)**
    *   *Kota Bekçisi (Quota Guard):* Sistemin bedava bir ERP gibi kullanılmasını engeller. Önemli kayıtlarda 500 işlem limiti vardır.
    *   *Susturucu (Notification Muter):* Simülasyon içinde yapılan işlemlerde (örn: Sipariş onaylandı), dış dünyaya gerçek E-Posta/SMS/WhatsApp gitmesi sistem seviyesinde engellenir.
4.  **Yaşam Döngüsü Yöneticisi (TTL Reaper)**
    *   Kural: Veriler ilk oluşturulduğu andan itibaren en fazla 6 ay yaşar. Ancak tarayıcıdan 2 hafta boyunca hiç girilmezse silinir.

---

## 3. İşleyiş Akışı (User Journey)

### Evre 1: İlk Temas ve İnşa
*   Kullanıcı sitede "Uygulamayı Dene / Playground" butonuna tıklar.
*   Sistem arka planda "Altın Şablon"dan kullanıcının kapsülünü yaratır.
*   (Opsiyonel) Bu aşamada kullanıcıdan temel Lead verisi (E-posta, İsim) alınabilir.

### Evre 2: Deneyim ve Rol Değiştirme (Aha! Moment)
*   Kullanıcı "Satış Temsilcisi" rolünü seçer. Yeni bir "Kumaş Satış Siparişi" girer.
*   Ekranın üstündeki menüden "Üretim Yöneticisi" rolüne geçer.
*   Sistem anında üretim ekranlarına geçer. Kullanıcı *az önce kendi girdiği satış siparişini* şimdi üretim yöneticisi gözüyle görür ve üretime alır. Uygulamanın departmanlar arası nasıl kusursuz konuştuğunu kendi kendine kanıtlar.

### Evre 3: Tutundurma (Retention)
*   Kullanıcı tarayıcıyı kapatıp gider. 3 gün sonra geri döndüğünde, tarayıcısındaki ID sayesinde sistem onu tanır: "Kaldığın yerden devam et".
*   Kullanıcı çok aktiftir ve limitlere yaklaşır (Örn: 480. işlemi yapar). Sistem nazikçe uyarır: "Deneyim sınırına yaklaşıyorsunuz. Verilerinizi kaybetmemek için kayıt olun."

### Evre 4: Karar Anı (Conversion / Registration)
Kullanıcı abone olmaya/satın almaya karar verdiğinde iki seçenek sunulur:
1.  **"Kaldığım Yerden Devam Et":** Geçici kapsül, "Gerçek Müşteri" kapsülüne dönüştürülür. 500 limiti kaldırılır, yaşam döngüsü süresi iptal edilir, bildirim susturucuları açılır.
2.  **"Temiz Başlangıç":** Kullanıcı sadece test için veri girdiğini belirtir. Eski geçici kapsül silinir, yepyeni tertemiz bir veritabanı (Tenant) açılır.

---

## Notlar & Gelecek İhtiyaçlar
*   *Uygulama zamanı geldiğinde:* `platform/tenant` altında `TenantType` (REGULAR, PLAYGROUND, TEMPLATE) yapısı kurulacak.
*   *Uygulama zamanı geldiğinde:* `platform/auth` tarafında guest_id ile impersonation JWT üreten özel bir controller yazılacak.
*   *Veritabanı stratejisi:* Golden Template klonlama işleminin JPA üzerinden mi yoksa hız için direkt SQL üzerinden mi yapılacağına o an karar verilecek.
