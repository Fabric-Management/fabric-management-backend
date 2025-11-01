# Database Schema Visualization Tools

Fabric Management veritabanı şemasını otomatik takip edip ERD (Entity Relationship Diagram) oluşturan araçlar.

## 1. ⭐ dbdiagram.io (ÖNERİLEN - EN KOLAY)

**Özellikler:**

- ✨ Tamamen ücretsiz
- 🌐 Web tabanlı, kurulum gerektirmez
- 🔄 PostgreSQL'i otomatik tarar
- 📊 Çok güzel ERD diyagramları üretir
- 💾 PDF, PNG olarak export
- 🔗 URL paylaşımı

**Nasıl Kullanılır:**

1. https://dbdiagram.io adresine git
2. PostgreSQL bağlantı bilgilerini gir
3. Otomatik şema çıkar
4. ERD oluştur

**Alternatif:** SQL dosyalarından import yapabilirsin

## 2. DBeaver (Ücretsiz - Desktop)

**Özellikler:**

- ✅ Tamamen ücretsiz ve açık kaynak
- 🖥️ Desktop uygulama
- 📊 Built-in ERD generator
- 🔄 Canlı DB bağlantısı
- 📝 SQL editör dahil
- 🔗 Foreign key ilişkilerini otomatik gösterir

**Kurulum:**

```bash
brew install --cask dbeaver-community
```

**ERD Oluşturma:**

1. Database'e bağlan
2. Sağ tık → View Diagram
3. Otomatik ilişkiler çıkarılır

## 3. pgAdmin (PostgreSQL Özel - Ücretsiz)

**Özellikler:**

- 🆓 Ücretsiz
- 🐘 PostgreSQL için optimize
- 📊 ERD tool dahil
- 🔍 Query editor güçlü

**Kurulum:**

```bash
brew install --cask pgadmin4
```

## 4. VS Code Extension: ERD Editor

**Özellikler:**

- 📝 VS Code içinde çalışır
- 🎨 Güzel diagramlar
- 💾 `.erd` dosyaları kaydeder
- 🔄 Mermaid formatı destekler

**Extension:**

- `ERD Editor` (By: detlef)
- `Database Client` (By: cweijan)

## 5. Online Tools (Web Tabanlı)

### dbml.io

- **URL:** https://dbdiagram.io veya https://github.com/holistics/dbml
- **Özellik:** Database Markup Language
- **Ücretsiz:** ✅

### SQLDock

- **URL:** https://sqldock.com
- **Özellik:** Convert PostgreSQL schema to ERD
- **Ücretsiz:** ✅ (limitli)

### QuickDBD

- **URL:** https://www.quickdatabasediagrams.com
- **Özellik:** Online ERD designer
- **Ücretsiz:** ✅ (sınırlı)

## 6. PostgreSQL Otomatik Şema Çıkarma

PostgreSQL'in kendi özellikleri:

```sql
-- Şemadaki tüm tabloları listele
SELECT
    schemaname,
    tablename
FROM pg_tables
WHERE schemaname IN ('common_company', 'common_user', 'production')
ORDER BY schemaname, tablename;

-- Foreign key ilişkilerini göster
SELECT
    tc.table_schema,
    tc.constraint_name,
    tc.table_name,
    kcu.column_name,
    ccu.table_schema AS foreign_table_schema,
    ccu.table_name AS foreign_table_name,
    ccu.column_name AS foreign_column_name
FROM information_schema.table_constraints AS tc
JOIN information_schema.key_column_usage AS kcu
    ON tc.constraint_name = kcu.constraint_name
    AND tc.table_schema = kcu.table_schema
JOIN information_schema.constraint_column_usage AS ccu
    ON ccu.constraint_name = tc.constraint_name
    AND ccu.table_schema = tc.table_schema
WHERE tc.constraint_type = 'FOREIGN KEY'
    AND tc.table_schema IN ('common_company', 'common_user', 'production')
ORDER BY tc.table_schema, tc.table_name;
```

## Önerilen Workflow

### Hızlı Başlangıç (5 dakika)

1. **dbdiagram.io** - Web'de bağlan, şemayı gör
2. PNG olarak kaydet, ekip ile paylaş

### Kalıcı Çözüm

1. **DBeaver** kurulumu yap
2. Her güncellemede otomatik yenile
3. Proje klasörüne ERD'leri kaydet

### CI/CD Entegrasyonu

```bash
# DBeaver headless mode ile otomatik ERD üret
# GitHub Actions'a eklenebilir
```

## VS Code Quick Setup

VS Code içinde kullanmak için:

```bash
code --install-extension cweijan.vscode-database-client2
code --install-extension kumquat.mysql-syntax
```

## Draw.io ile Entegrasyon

Draw.io'da PostgreSQL şemalarını görüntülemek için:

1. DBeaver'dan ERD'yi PNG olarak export et
2. Draw.io'ya import et
3. Düzenle ve customize et

## En Hızlı Çözüm (Şimdi)

**dbdiagram.io** ile 5 dakikada çalıştır:

1. https://dbdiagram.io → "Import from Database"
2. PostgreSQL connection string:
   ```
   postgresql://user:pass@localhost:5432/fabric_management
   ```
3. "Generate ERD" tıkla
4. Export PNG veya PDF

## Project'e Eklenebilir

Şemaları projeye commit etmek için:

```bash
mkdir docs/diagrams
# DBeaver ERD export → docs/diagrams/erd-latest.png
git add docs/diagrams/
```

## Fiyat Karşılaştırması

| Tool         | Ücretsiz | Desktop | Web | ERD        | Öneri               |
| ------------ | -------- | ------- | --- | ---------- | ------------------- |
| dbdiagram.io | ✅       | ❌      | ✅  | ⭐⭐⭐⭐⭐ | **En Kolay**        |
| DBeaver      | ✅       | ✅      | ❌  | ⭐⭐⭐⭐   | **En Kapsamlı**     |
| pgAdmin      | ✅       | ✅      | ❌  | ⭐⭐⭐     | PostgreSQL Uzmanı   |
| VS Code Ext  | ✅       | ✅      | ❌  | ⭐⭐⭐     | Editör Entegrasyonu |

## Tavsiye

✨ **dbdiagram.io** - Hemen başla, 5 dakikada çalışır
🛠️ **DBeaver** - Uzun vadeli kullanım için en iyi

## 🚀 Hızlı Başlangıç (dbdiagram.io ile)

Projedeki hazır schema dosyası ile:

1. Git: https://dbdiagram.io
2. "Code Editor" modunu aç (sol üst)
3. Şu dosyanın içeriğini kopyala yapıştır:
   ```
   docs/dbdiagram-schema.dbml
   ```
4. "Generate ERD" butonuna tıkla
5. PNG veya PDF olarak export et

**Alternatif:** "Import from Database" bölümünden PostgreSQL bağlantısı yap
