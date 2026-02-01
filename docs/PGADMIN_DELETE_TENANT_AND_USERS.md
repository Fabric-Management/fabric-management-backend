# pgAdmin'de Tenant ve Kullanıcı Silme

Bu projede **tenant** = `common_company.common_company` satırı (root tenant için `tenant_id = id`). **Kullanıcı** = `common_user.common_user` satırı.

---

## 1. Tenant ve kullanıcıları listeleme

**Tüm tenant’lar (şirketler):**

```sql
SELECT id, tenant_id, uid, company_name, tax_id, company_type
FROM common_company.common_company
ORDER BY created_at;
```

**Bir tenant’a ait kullanıcılar:**

```sql
-- <TENANT_ID> yerine silmek istediğiniz tenant'ın id değerini yazın
SELECT id, uid, first_name, last_name, display_name, company_id, onboarding_completed_at
FROM common_user.common_user
WHERE tenant_id = '<TENANT_ID>';
```

**Sistem tenant’ını silmeyin:** `tenant_id = '00000000-0000-0000-0000-000000000000'` olan kayıt platforma ait sistem tenant’ıdır; silinmemeli.

---

## 2. Tek bir kullanıcı silme

Silinecek **user id**’yi biliyorsunuz (yukarıdaki sorgudan veya başka yerden).

**Adım 1 – Kullanıcının refresh token’larını sil:**

```sql
DELETE FROM common_auth.common_refresh_token
WHERE user_id = '<USER_ID>';
```

**Adım 2 – Kullanıcıyı sil (auth, user_contact, user_department vb. CASCADE ile silinir):**

```sql
DELETE FROM common_user.common_user
WHERE id = '<USER_ID>';
```

**Not:** `common_auth_user` ve `common_communication.user_contact` gibi tablolar `user_id` için `ON DELETE CASCADE` kullanıyorsa, kullanıcı silindiğinde bu satırlar otomatik silinir. `common_refresh_token` için FK yok; bu yüzden önce onu siliyoruz.

---

## 3. Tek bir tenant (şirket) silme

**Dikkat:** Tüm tenant verisi silinir (kullanıcılar, auth, contact, department, subscription vb.). Geri alınamaz.

`<TENANT_ID>` = Silinecek tenant’ın id’si (genelde `common_company.common_company.id` = root tenant için `tenant_id` ile aynı).

**Sırayla çalıştırın (Query Tool’da tek tek veya blok halinde):**

```sql
-- 1) Auth: refresh token'lar
DELETE FROM common_auth.common_refresh_token
WHERE tenant_id = '<TENANT_ID>';

-- 2) Auth: verification code'lar
DELETE FROM common_auth.common_verification_code
WHERE tenant_id = '<TENANT_ID>';

-- 3) Auth: registration token'lar
DELETE FROM common_auth.common_registration_token
WHERE tenant_id = '<TENANT_ID>';

-- 4) Auth: auth user (şifre kayıtları)
DELETE FROM common_auth.common_auth_user
WHERE tenant_id = '<TENANT_ID>';

-- 5) User–contact ilişkisi (tenant'taki kullanıcılar)
DELETE FROM common_communication.common_user_contact
WHERE tenant_id = '<TENANT_ID>';

-- 6) User–department ilişkisi
DELETE FROM common_user.common_user_department
WHERE user_id IN (SELECT id FROM common_user.common_user WHERE tenant_id = '<TENANT_ID>');

-- 7) User–position ilişkisi
DELETE FROM common_user.common_user_position
WHERE user_id IN (SELECT id FROM common_user.common_user WHERE tenant_id = '<TENANT_ID>');

-- 8) İsteğe bağlı: profile update request
DELETE FROM common_user.profile_update_request
WHERE user_id IN (SELECT id FROM common_user.common_user WHERE tenant_id = '<TENANT_ID>');

-- 9) Human modülü (employee vb.) varsa – tablo adları migration'lara göre değişebilir
-- DELETE FROM human.human_employee WHERE user_id IN (SELECT id FROM common_user.common_user WHERE tenant_id = '<TENANT_ID>');

-- 10) Contact'lar (tenant'a ait)
DELETE FROM common_communication.common_contact
WHERE tenant_id = '<TENANT_ID>';

-- 11) Address'ler (tenant'a ait) – junction sonra silinmeli; V034'te address_contact var
DELETE FROM common_communication.common_address_contact
WHERE address_id IN (SELECT id FROM common_communication.common_address WHERE tenant_id = '<TENANT_ID>');
DELETE FROM common_communication.common_company_address
WHERE company_id = '<TENANT_ID>';
DELETE FROM common_communication.common_user_address
WHERE user_id IN (SELECT id FROM common_user.common_user WHERE tenant_id = '<TENANT_ID>');
DELETE FROM common_communication.common_address
WHERE tenant_id = '<TENANT_ID>';

-- 12) Company–contact ilişkisi
DELETE FROM common_communication.common_company_contact
WHERE company_id = '<TENANT_ID>';

-- 13) Kullanıcılar
DELETE FROM common_user.common_user
WHERE tenant_id = '<TENANT_ID>';

-- 14) Department, position, subscription vb. (common_company şeması)
DELETE FROM common_user.common_user_department
WHERE department_id IN (SELECT id FROM common_company.common_department WHERE tenant_id = '<TENANT_ID>');
-- Position ve department silme sırası migration'lara göre farklı olabilir; FK hata alırsanız sırayı ayarlayın
DELETE FROM common_company.common_position
WHERE tenant_id = '<TENANT_ID>';
DELETE FROM common_company.common_department
WHERE tenant_id = '<TENANT_ID>';
DELETE FROM common_company.common_subscription
WHERE tenant_id = '<TENANT_ID>';

-- 15) Şirket (tenant) kaydı
DELETE FROM common_company.common_company
WHERE id = '<TENANT_ID>';
```

**FK hatası alırsanız:** Hangi tablonun referans verdiği mesajda yazar. O tabloyu daha önce silmeniz veya sırayı o tabloya göre değiştirmeniz gerekir (örn. human, payroll, production şemalarındaki tablolar). Bu dokümandaki sıra temel modüller içindir; ek modül varsa o tabloları da benzer mantıkla ekleyin.

---

## 4. pgAdmin’de pratik kullanım

1. **Serverse bağlan** → **Databases** → `fabric_management` (veya kullandığınız DB) → **Query Tool**.
2. **Tenant id bul:** Yukarıdaki “Tüm tenant’lar” sorgusunu çalıştır; silinecek satırın `id` değerini kopyala.
3. **Tek kullanıcı silmek:** “Tek bir kullanıcı silme” bölümündeki SQL’de `<USER_ID>` ve gerekirse `<TENANT_ID>` yerine bu id’leri yapıştır; sırayla çalıştır.
4. **Tüm tenant silmek:** “Tek bir tenant silme” bölümündeki tüm blokta `<TENANT_ID>` yerine tenant id’yi yazıp sırayla çalıştırın. Hata alırsanız hata mesajındaki tabloyu da silme sırasına ekleyin.

**Yedek:** Silmeden önce ilgili tablolarda `SELECT * FROM ... WHERE tenant_id = '<TENANT_ID>'` veya `WHERE id = '<USER_ID>'` ile yedek almanız iyi olur.
