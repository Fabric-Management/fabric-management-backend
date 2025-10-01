# 🔍 FLYWAY MIGRATION TEMİZLİK ANALİZİ

## 📊 MEVCUT DURUM

### **User Service Migration Dosyaları:**

```
V1__create_user_tables.sql      ✅ ANA DOSYA
V3__remove_user_contacts_table.sql  ❌ GEREKSİZ (V2 yok!)
V4__add_missing_audit_columns.sql   ❌ GEREKSİZ (patch dosyası)
```

### **Contact Service:**

```
V1__create_contact_tables.sql   ✅ TEMİZ
```

### **Company Service:**

```
V1__create_company_tables.sql   ✅ TEMİZ
```

## 🚨 TESPİT EDİLEN SORUNLAR

### 1. **V3 VAR AMA V2 YOK!**

```sql
V3__remove_user_contacts_table.sql
-- DROP TABLE IF EXISTS user_contacts CASCADE;
```

**SORUN:**

- V1'den V3'e atlama var
- V2 hiç yok
- Bu kötü bir versiyon yönetimi!

### 2. **V4 PATCH DOSYASI**

```sql
V4__add_missing_audit_columns.sql
-- ALTER TABLE ... ADD COLUMN IF NOT EXISTS
```

**SORUN:**

- Bu bir "yama" dosyası
- V1'de olması gerekenler sonradan ekleniyor
- Her yeni ortamda 4 migration çalışacak (gereksiz)

### 3. **V1'DE user_contacts TABLOSU VAR**

```sql
-- V1__create_user_tables.sql içinde:
CREATE TABLE IF NOT EXISTS user_contacts (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    ...
);
```

**SORUN:**

- V1'de oluşturuluyor
- V3'te siliniyor
- Hiç oluşturmasak daha iyi!

## ✅ BEST PRACTICE ÇÖZÜM

### **YAKLAŞIM: V1'İ TEMİZLE, DİĞERLERİNİ SİL**

Çünkü:

- Henüz production'da değiliz
- Database'ler temiz başlatılacak
- V1'i doğru yapıp, tek migration ile başlamalıyız

### **YENİ TEMİZ V1 İÇERMELİ:**

1. ✅ **users tablosu** - Tam ve doğru kolonlarla
2. ✅ **password_reset_tokens** - Tüm audit kolonları ile
3. ✅ **user_sessions** - Tüm audit kolonları ile
4. ✅ **user_events** - Tüm audit kolonları ile
5. ✅ **user_preferences** - ElementCollection için
6. ✅ **user_settings** - ElementCollection için
7. ❌ **user_contacts** - OLMAYACAK (Contact Service'de)

## 🎯 UYGULAMA PLANI

### **Aşama 1: V1'i Güncelle**

```sql
-- Tüm tabloları doğru şekilde oluştur
-- user_contacts tablosunu ekleme
-- Tüm audit kolonlarını dahil et
```

### **Aşama 2: V3 ve V4'ü Sil**

```bash
rm V3__remove_user_contacts_table.sql
rm V4__add_missing_audit_columns.sql
```

### **Aşama 3: flyway_schema_history Temizle**

```sql
-- Yeni database'de otomatik olarak sadece V1 çalışacak
```

## 📋 YENİ V1 İÇERİĞİ

### **Değişiklikler:**

1. **user_contacts tablosunu ÇIKAR**

   - Contact Service'de olacak
   - User Service'e ait değil

2. **password_reset_tokens'a audit ekle:**

   ```sql
   created_by VARCHAR(100),
   updated_by VARCHAR(100),
   updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
   version BIGINT DEFAULT 0,
   deleted BOOLEAN DEFAULT FALSE
   ```

3. **user_sessions'a audit ekle:**

   ```sql
   created_by VARCHAR(100),
   updated_by VARCHAR(100),
   updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
   version BIGINT DEFAULT 0,
   deleted BOOLEAN DEFAULT FALSE
   ```

4. **user_events'e audit ekle:**

   ```sql
   created_by VARCHAR(100),
   updated_by VARCHAR(100),
   updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
   version BIGINT DEFAULT 0,
   deleted BOOLEAN DEFAULT FALSE
   ```

5. **users tablosuna invitation_token ekle:**

   ```sql
   invitation_token VARCHAR(255)
   ```

6. **user_preferences ve user_settings tablolarını ekle:**

   ```sql
   CREATE TABLE user_preferences (
       user_id UUID NOT NULL,
       preference_key VARCHAR(255) NOT NULL,
       preference_value VARCHAR(1000),
       PRIMARY KEY (user_id, preference_key),
       CONSTRAINT fk_preference_user FOREIGN KEY (user_id)
           REFERENCES users(id) ON DELETE CASCADE
   );

   CREATE TABLE user_settings (
       user_id UUID NOT NULL,
       setting_key VARCHAR(255) NOT NULL,
       setting_value VARCHAR(1000),
       PRIMARY KEY (user_id, setting_key),
       CONSTRAINT fk_setting_user FOREIGN KEY (user_id)
           REFERENCES users(id) ON DELETE CASCADE
   );
   ```

## ✅ SONUÇ

**Şu anki durum:**

- 3 migration dosyası (V1, V3, V4)
- Karmaşık ve tutarsız
- V2 eksik

**Hedef durum:**

- 1 migration dosyası (V1)
- Temiz ve eksiksiz
- Tek seferde doğru şekilde çalışan

**Kazanç:**

- %67 daha az migration dosyası
- Sıfır karmaşıklık
- Her yeni ortamda hızlı kurulum

---

_Rapor Tarihi: 01 Ekim 2025_
_Durum: V1'DE KALMALIYIZ_
_Öneri: V3 ve V4'ü sil, V1'i güncelle_

