# Maliyet Tanımları — CostItem & CostTemplate

> Modül: Maliyet Yönetimi (06-costing) | Versiyon: 1.0 | Son güncelleme: 2026-03-17
> Kanonik kaynak: CostItem ve CostTemplate burada tanımlanır.

## 8 Global Maliyet Kalemi

RAW_MATERIAL (Hammadde), LABOR (İşçilik), MACHINE (Makine/ekipman), ENERGY (Enerji), OVERHEAD (Genel gider), LOGISTICS (Nakliye), QUALITY (Kalite kontrol), PACKAGING (Paketleme).

## Modüle Özel Ek Kalemler

Fiber: Elyaf ayırma, balyalama, nem ayarı. Yarn: Büküm, katlama, bobin sarım. Fabric: Dokuma/örgü, haşıl, çözgü. Dye: Boya kimyasalı, su tüketimi, apre.

## CostItem (`costing.cost_item`)

code (benzersiz), name, description, scope (GLOBAL/MODULE_SPECIFIC), moduleType, calculationBase (PER_KG/PER_HOUR/PER_UNIT/PERCENTAGE/FIXED), displayOrder, isActive. Sistem tanımlı — tenant ekleyemez.

## CostTemplate (`costing.cost_template`)

name, moduleType, isDefault, items JSONB (hangi CostItem'lar dahil + ağırlıkları). Tenant yapılandırabilir.
