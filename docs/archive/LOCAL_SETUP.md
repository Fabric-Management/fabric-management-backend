# 🖥️ Yerel Geliştirme Kurulumu (Docker Olmadan)

## Gereksinimler

- **Java 21** ve **Maven** (veya proje kökünde `./mvnw` — Maven Wrapper)
- Maven yüklü değilse, bir kez `mvn -N wrapper:wrapper` çalıştırarak `mvnw` oluşturabilirsiniz; sonrasında `make` komutları `./mvnw` kullanır.

## 1. PostgreSQL Kurulumu

### macOS (Homebrew ile):
```bash
# PostgreSQL kurulumu
brew install postgresql@15

# PostgreSQL servisini başlat
brew services start postgresql@15

# PostgreSQL'in çalıştığını kontrol et
psql --version
```

### Alternatif: PostgreSQL.app (GUI)
- https://postgresapp.com/ adresinden indirin
- Uygulamayı başlatın

## 2. Veritabanı ve Kullanıcı Oluşturma

PostgreSQL kurulduktan sonra:

```bash
# PostgreSQL'e bağlan (varsayılan kullanıcı ile)
psql postgres

# Veritabanı ve kullanıcı oluştur
CREATE DATABASE fabric_management;
CREATE USER fabric_user WITH PASSWORD 'local_dev_2024';
GRANT ALL PRIVILEGES ON DATABASE fabric_management TO fabric_user;

# PostgreSQL'den çık
\q
```

## 3. Uygulamayı Başlatma

```bash
# Uygulamayı yerelde çalıştır
make app-run
```

Uygulama şu adreste çalışacak:
- **Backend**: http://localhost:8081
- **Swagger UI**: http://localhost:8081/swagger-ui.html
- **Health Check**: http://localhost:8081/actuator/health

## 4. Veritabanı Migrasyonları

Uygulama ilk başlatıldığında Flyway otomatik olarak migrasyonları çalıştıracak.

Manuel olarak çalıştırmak isterseniz:
```bash
make db-migrate
```

## Sorun Giderme

### PostgreSQL bağlantı hatası:
```bash
# PostgreSQL'in çalıştığını kontrol et
brew services list | grep postgres

# Veya
ps aux | grep postgres
```

### Port çakışması:
Eğer 5432 portu kullanılıyorsa, `.env` dosyasında `POSTGRES_PORT=5433` olarak ayarlayın.

### Veritabanı bulunamadı:
```bash
# Veritabanlarını listele
psql -l

# Veritabanını yeniden oluştur
psql postgres -c "DROP DATABASE IF EXISTS fabric_management;"
psql postgres -c "CREATE DATABASE fabric_management;"
```
