# Fiyat Listeleri — PriceList & VolumePriceBreak

> Modül: Maliyet Yönetimi (06-costing) | Versiyon: 1.0 | Son güncelleme: 2026-03-17
> Kanonik kaynak: PriceList, PriceListItem, VolumePriceBreak burada tanımlanır.

## PriceList (`costing.price_list`)

name, moduleType, currency, validFrom, validUntil, isActive, seasonTag.

## PriceListItem (`costing.price_list_item`)

priceListId FK, costItemCode FK→CostItem.code, materialId (nullable — hammadde spesifik), tradingPartnerId (nullable — null ise genel fiyat, dolu ise sözleşmeli), unitPrice, unit, currency.

> tradingPartnerId eklendi — anlaşmalı tedarikçi direkt PO oluşturma için.

## VolumePriceBreak (`costing.volume_price_break`)

priceListItemId FK, minQty, maxQty, unitPrice, discountRate.

## Mevsimsel Fiyatlama

seasonTag ile yönetilir. Maliyet hesaplanırken aktif PriceList seçilir.

## Procurement Entegrasyonu

GoodsReceipt CONFIRMED → PriceListItem.unitPrice güncellenir (fiili fiyat).
