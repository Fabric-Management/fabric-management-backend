# 3 Aşamalı Maliyet Hesabı

> Modül: Maliyet Yönetimi (06-costing) | Versiyon: 1.0 | Son güncelleme: 2026-03-17
> Kanonik kaynak: CostCalculation ve CostCalculationLine burada tanımlanır.

## Genel Bakış

Tek bir sabit sayı değil — tahmini → planlı → gerçek olmak üzere 3 aşamalı maliyet zinciri.

## CostCalculation (`costing.cost_calculation`)

entityType (QUOTE/WORK_ORDER/BATCH — polimorfik), entityId, moduleType, costTemplateId FK, stage (ESTIMATED/PLANNED/ACTUAL), totalCost, currency, calculatedAt, exchangeRateSnapshotId, notes.

## CostCalculationLine (`costing.cost_calculation_line`)

costCalculationId FK, costItemCode, qty, unit, unitPrice, currency, totalInBaseCurrency, exchangeRate, volumeDiscountApplied, notes.

## 3 Aşama

**ESTIMATED (Quote):** PriceList + overhead → Quote.estimatedUnitCost. DiscountPolicy kar marjı kontrolü.

**PLANNED (WorkOrder):** Gerçek tedarikçi fiyatı + lot maliyet → WorkOrder.plannedCost.

**ACTUAL (Batch):** Fiili harcamalar (fire, enerji, işçilik) → Batch.actualCost.

## Maliyet Nereden Hesaplanıyor?

Recipe.components → her fiber için PriceList'ten birim fiyat → percentage ile çarp → overhead ekle (CostTemplate'ten). Recipe'ye unitCost alanı EKLENMEZ — maliyet bu service'te hesaplanır.

## Sapma Analizi

Tahmini → Planlı sapma: hammadde fiyat değişimi. Planlı → Gerçek sapma: fire, verimlilik, enerji.

## Event: CostVarianceDetected → FlowBoard (COSTING task) + NotificationHub (HIGH)
