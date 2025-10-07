# 📊 Monitoring Configuration

## 🎯 Durum: HAZIR AMA AKTİF DEĞİL

Bu klasör, Fabric Management System için Prometheus ve Grafana monitoring stack'inin konfigürasyon dosyalarını içerir.

**Mevcut Durum:** ⏸️ Yapılandırılmış ama deploy edilmemiş

---

## 📁 İçerik

```
monitoring/
└── prometheus/
    └── prometheus.yml    # Prometheus scrape configuration
```

---

## 🚀 Nasıl Aktif Edilir?

### 1. docker-compose.monitoring.yml Oluşturun

```yaml
# docker-compose.monitoring.yml
version: "3.8"

services:
  prometheus:
    image: prom/prometheus:latest
    container_name: fabric-prometheus
    volumes:
      - ./monitoring/prometheus/prometheus.yml:/etc/prometheus/prometheus.yml
      - prometheus_data:/prometheus
    ports:
      - "9090:9090"
    networks:
      - fabric-network
    command:
      - "--config.file=/etc/prometheus/prometheus.yml"
      - "--storage.tsdb.path=/prometheus"

  grafana:
    image: grafana/grafana:latest
    container_name: fabric-grafana
    ports:
      - "3000:3000"
    environment:
      - GF_SECURITY_ADMIN_PASSWORD=admin
    volumes:
      - grafana_data:/var/lib/grafana
    networks:
      - fabric-network

volumes:
  prometheus_data:
  grafana_data:

networks:
  fabric-network:
    external: true
```

### 2. Deploy

```bash
# Monitoring stack'i başlat
docker-compose -f docker-compose.monitoring.yml up -d

# Erişim
# Prometheus: http://localhost:9090
# Grafana: http://localhost:3000 (admin/admin)
```

---

## 📊 Yapılandırılmış Metrikler

### Microservices

- ✅ User Service - `/actuator/prometheus`
- ✅ Contact Service - `/actuator/prometheus`
- ✅ Company Service - `/actuator/prometheus`

### Infrastructure

- ✅ PostgreSQL (postgres-exporter gerekli)
- ✅ Redis (redis-exporter gerekli)

### JMX Monitoring

- ✅ User Service JMX (port 9010)
- ✅ Contact Service JMX (port 9010)
- ✅ Company Service JMX (port 9010)

---

## 🎯 Ne Zaman Aktif Etmeli?

### Development

- 🟡 **Opsiyonel** - Local development'ta genelde gerekmiyor

### Staging

- 🟢 **Önerilen** - Performance testing için faydalı

### Production

- 🔴 **ZORUNLU** - Monitoring production'da kritik

---

## 📚 İlgili Dokümantasyon

- [DEPLOYMENT_GUIDE.md](../docs/deployment/DEPLOYMENT_GUIDE.md) - Monitoring deployment
- [PROJECT_STRUCTURE.md](../docs/PROJECT_STRUCTURE.md) - Proje yapısı

---

## 🔮 Gelecek Geliştirmeler

- [ ] Grafana dashboard templates ekle
- [ ] Alert rules yapılandır
- [ ] Service mesh metrics (Istio)
- [ ] Custom business metrics

---

**Durum:** 📦 Ready for Production
**Son Güncelleme:** 2025-10-03
**Versiyon:** 1.0.0
