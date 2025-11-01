🧭 Fabric Management – Role & Department Architecture Action Plan

Author: Fatih Akkaya
Date: 2025-11-01
Purpose: Kurumsal ölçekte, dinamik ve veritabanı tabanlı bir Role / Department / DepartmentCategory mimarisi kurmak.

Phase 1 – Foundational Structure (High Priority)

Amaç: Statik string temelli alanları ilişkisel yapılara dönüştürmek.

Görevler

 Role, DepartmentCategory ve User-Department tablolarını oluştur.

 Department tablosuna department_category_id FK ekle.

 User ↔ Department için junction table kur.

 User → Role ilişkisini tanımla.

 Flyway migrasyonlarını güncelle.

 User.department alanını geçici olarak koru.

Sonuç: Veritabanı şeması ve ilişkiler başarıyla çalışıyor.

Phase 2 – Entity Relationship Integration (High Priority)

Amaç: Yeni şema ile entity’leri uyumlu hale getirmek.

Görevler

 @ManyToOne (Department → Category)

 @ManyToMany (User ↔ Department)

 @ManyToOne (User → Role)

 Eski User.department kullanımlarını temizle.

 Servis ve mapper’ları ilişkisel modele göre düzenle.

Sonuç: Uygulama ilişkisel verilerle sorunsuz çalışıyor.

Phase 3 – Management Endpoints (Medium Priority)

Amaç: CRUD API’leri ile rolleri ve departmanları dinamik yönetilebilir hale getirmek.

Görevler

 RoleController, DepartmentCategoryController, DepartmentController oluştur.

 User-Department ve User-Role atama endpoint’leri.

 Soft-delete ve input validation ekle.

 Swagger dokümantasyonunu güncelle.

Sonuç: Yönetim paneli üzerinden kod değişmeden veri yönetimi yapılabiliyor.

Phase 4 – Policy & Authorization Refactor (Medium Priority)

Amaç: String tabanlı politikaları entity tabanlı hale getirmek.

Görevler

 Policy koşullarında UUID / code kullan.

 PolicyService’de DB lookup ile değerlendirme yap.

 Policy oluştururken Role / Department doğrulaması ekle.

 JWT payload’ında yalnızca görsel amaçlı kimlik bilgilerini koru.

Sonuç: Yetkilendirme gerçek veritabanı nesnelerine dayanıyor.

Phase 5 – Migration & Seed Data (Low Priority)

Amaç: Temel verileri otomatik yüklemek ve eski veriyi güvenli şekilde taşımak.

🧾 Seed Data (Tablolar ve İçerikler)
Department Categories
category_name	description
Production	Üretim ile doğrudan ilgili departmanlar
Administrative	Ofis / yönetim / destek birimleri
Utility	Yardımcı hizmet birimleri
Logistics & Warehouse	Depo / sevkiyat / stok operasyonları
Support & Audit	Eğitim / dokümantasyon / denetim birimleri
Departments
department_name	category_name
Spinning	Production
Weaving	Production
Dyehouse	Production
Finishing	Production
Quality Control	Production
Planning	Production
Accounting	Administrative
Human Resources	Administrative
IT	Administrative
Procurement	Administrative
Sales	Administrative
Kitchen	Utility
Cleaning	Utility
Security	Utility
Warehouse	Logistics & Warehouse
Dispatch	Logistics & Warehouse
Training	Support & Audit
Audit	Support & Audit
Roles
role_name	role_code	description
Administrator	ADMIN	Full system access
Director	DIRECTOR	Üst yönetim erişimi
Manager	MANAGER	Departman yönetimi
Supervisor	SUPERVISOR	Vardiya / ekip lideri
User	USER	Standart çalışan
Intern	INTERN	Stajyer erişimi
Viewer	VIEWER	Sadece okuma yetkisi
🔹 Tasks

 Yukarıdaki veriler için seed migrasyon dosyası (Vxxx__seed_role_department_data.sql) oluştur.

 Eski User.department verisini UserDepartment tablosuna taşı.

 FK’lar ve referential integrity’yi doğrula.

 Tablolar ilk çalışmada otomatik dolmalı (tek seferlik insert if missing).

Sonuç: Uygulama ilk açıldığında tüm temel roller, departmanlar ve kategoriler hazır olur.

Phase 6 – Test Coverage (After Core Stability)

Amaç: Yapı oturduktan sonra test altyapısını genişletmek.

Görevler

 Repository ve Controller testleri ekle.

 User-Role / Department entegrasyon testleri.

 Mock data helper sınıfları oluştur.

Sonuç: Kararlı test altyapısı ve izlenebilir kapsam.

Phase 7 – Post-Implementation Review

Amaç: Yeni mimarinin doğruluğunu ve sürdürülebilirliğini onaylamak.

Görevler

 Schema ve ilişkileri ekip ile gözden geçir.

 Admin endpoint’lerini test et.

 Policy senaryolarını doğrula.

 README ve ARCHITECTURE.md dokümantasyonlarını güncelle.

Sonuç: Yeni yapı tamamen doğrulanmış, üretim-hazır.

Success Criteria

Roller, departmanlar ve kategoriler tamamen veritabanı tabanlı.

Tüm ilişkiler foreign key ile yönetiliyor.

Yönetim paneli CRUD işlemlerini kod değişmeden yapabiliyor.

Policy kontrolü gerçek varlıklara dayanıyor.

Test altyapısı genişletilmeye hazır.