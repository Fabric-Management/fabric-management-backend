# JSONB Kullanım Stratejisi

> Modül: Cross-Cutting (11-cross-cutting) | Versiyon: 1.0 | Son güncelleme: 2026-03-17

## Temel Kural

JSONB doğru: yapısı değişken, modüle göre farklılaşan, raporlama boyutu olmayan veriler.
JSONB yanlış: sık filtrelenen, JOIN'lenen, raporlama boyutu olan veriler.

## JSONB Kalacak Alanlar

| Alan | Gerekçe |
|---|---|
| WorkOrder.attachments | Dosya URL listesi — raporlamada yok |
| SalesOrderLine.moduleSpecs | Modüle göre değişken şema |
| TradingPartner.relationshipMeta | Tenant'a göre değişken yapı |
| CostTemplate.items | Tanım verisi, sık sorgulanmıyor |
| Quote.approvalEvidence | Kanıt verisi — raporlamada yok |
| RMA.photoEvidence | Fotoğraf URL — raporlamada yok |

## Hibrit Yaklaşım (JSONB + Ayrı Tablo)

**Recipe.components → RecipeComponent ayrı tablo eklendi.** JSONB hızlı okuma (UI kartı), ayrı tablo hızlı sorgulama (RuleEngine, raporlama). İkisi aynı transaction'da yazılır.

## Ayrı Tabloya Taşınabilecek (İleride)

SubcontractOrder.materialSent → fire takibi detaylandırılınca.

## Index Stratejisi

moduleSpecs üzerinde GIN index:
```sql
CREATE INDEX idx_sol_module_specs ON sales.sales_order_line USING GIN (module_specs jsonb_path_ops);
```

RecipeComponent üzerinde standard index (JSONB yerine ayrı tablo kullanıldığı için):
```sql
CREATE INDEX idx_rc_certification ON production.prod_recipe_component (certification);
CREATE INDEX idx_rc_origin ON production.prod_recipe_component (origin);
```
