-- ============================================
-- MODULE: TENANT
-- Birleştirilen migration'lar: V045
-- Not: common_tenant.common_tenant tablosu FK bağımlılığı nedeniyle V001__COMMON_module.sql
--      içinde oluşturulur (organization, user, trading partner vb. tenant_id ile referans verir).
--      Bu dosya ileride tenant'a özgü ek tablolar için ayrıldı.
-- ============================================

-- common_tenant schema ve common_tenant tablosu V001 COMMON'da oluşturuldu.
-- Bu modül şu an boş; ileride tenant-specific tablolar eklenebilir.

-- [TENANT] module migration tamamlandı.
-- Tablo sayısı: 0 (tenant tablosu V001'de)
-- Toplam index sayısı: 0
