# Döviz Kuru & Tarihsel Kayıt

> Modül: Maliyet Yönetimi (06-costing) | Versiyon: 1.0 | Son güncelleme: 2026-03-17
> Kanonik kaynak: ExchangeRateSnapshot ve CostHistory burada tanımlanır.

## ExchangeRateSnapshot (`costing.exchange_rate_snapshot`)

baseCurrency (TRY), targetCurrency, rate, source (TCMB/ECB/manual), capturedAt.

Neden snapshot? Kur değişirse geçmiş hesaplamalar etkilenmesin.

## CostHistory (`costing.cost_history`)

costItemCode, moduleType, materialId, unitPrice, currency, validFrom, validUntil, changeReason, seasonTag.

## Trend Analizi

CostHistory üzerinden: "Cotton fiyatı son 2 yılda nasıl değişti?", "Hangi mevsimde en yüksek?", "Yıllık ortalama artış?"

## Açık Kararlar

- Döviz kuru otomatik çekme — TCMB API mi, ECB mi?
- Kur güncelleme sıklığı — günlük mi, anlık mı?
