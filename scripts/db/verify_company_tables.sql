-- ============================================================================
-- COMPANY TABLOLARINI DOĞRULAMA SORGULARI
-- ============================================================================
-- PGAdmin'de bu sorguları çalıştırarak Company yapısını doğrulayabilirsiniz
-- ============================================================================

-- 1. SCHEMA VAR MI?
-- ============================================================================
SELECT 
    'Schema Check' AS check_type,
    CASE 
        WHEN EXISTS(SELECT 1 FROM information_schema.schemata WHERE schema_name = 'common_company')
        THEN '✅ common_company schema mevcut'
        ELSE '❌ common_company schema bulunamadı'
    END AS result;

-- 2. TÜM SCHEMA'LARI LİSTELE
-- ============================================================================
SELECT 
    schema_name,
    schema_owner
FROM information_schema.schemata
WHERE schema_name LIKE 'common_%'
ORDER BY schema_name;

-- 3. COMMON_COMPANY SCHEMA'SUNDAKI TÜM TABLOLAR
-- ============================================================================
SELECT 
    schemaname,
    tablename,
    tableowner,
    pg_size_pretty(pg_total_relation_size(schemaname||'.'||tablename)) AS size
FROM pg_tables
WHERE schemaname = 'common_company'
ORDER BY tablename;

-- 4. COMMON_COMPANY TABLOSUNUN YAPISI
-- ============================================================================
SELECT 
    column_name,
    data_type,
    character_maximum_length,
    is_nullable,
    column_default,
    ordinal_position
FROM information_schema.columns
WHERE table_schema = 'common_company'
AND table_name = 'common_company'
ORDER BY ordinal_position;

-- 5. COMMON_COMPANY TABLOSUNDAKI KAYIT SAYISI
-- ============================================================================
SELECT 
    COUNT(*) AS total_companies,
    COUNT(DISTINCT tenant_id) AS unique_tenants,
    COUNT(*) FILTER (WHERE is_active = TRUE) AS active_companies,
    COUNT(*) FILTER (WHERE is_active = FALSE) AS inactive_companies
FROM common_company.common_company;

-- 6. COMMON_COMPANY TABLOSUNDAKI İLK 10 KAYIT
-- ============================================================================
SELECT 
    id,
    uid,
    company_name,
    tax_id,
    company_type,
    tenant_id,
    parent_company_id,
    is_active,
    created_at
FROM common_company.common_company
ORDER BY created_at DESC
LIMIT 10;

-- 7. COMPANY TYPE DAĞILIMI
-- ============================================================================
SELECT 
    company_type,
    COUNT(*) AS count,
    COUNT(*) FILTER (WHERE is_active = TRUE) AS active_count
FROM common_company.common_company
GROUP BY company_type
ORDER BY count DESC;

-- 8. DEPENDENT TABLOLAR (COMPANY'YE BAĞLI)
-- ============================================================================
SELECT
    'common_department' AS dependent_table,
    COUNT(*) AS record_count
FROM common_company.common_department
UNION ALL
SELECT
    'common_subscription',
    COUNT(*)
FROM common_company.common_subscription
UNION ALL
SELECT
    'common_company_contact',
    COUNT(*)
FROM common_communication.common_company_contact
UNION ALL
SELECT
    'common_company_address',
    COUNT(*)
FROM common_communication.common_company_address;

-- 9. FOREIGN KEY İLİŞKİLERİ
-- ============================================================================
SELECT
    tc.table_schema,
    tc.table_name AS source_table,
    kcu.column_name AS source_column,
    ccu.table_schema AS target_schema,
    ccu.table_name AS target_table,
    ccu.column_name AS target_column,
    tc.constraint_name
FROM information_schema.table_constraints AS tc
JOIN information_schema.key_column_usage AS kcu
    ON tc.constraint_name = kcu.constraint_name
    AND tc.table_schema = kcu.table_schema
JOIN information_schema.constraint_column_usage AS ccu
    ON ccu.constraint_name = tc.constraint_name
    AND ccu.table_schema = tc.table_schema
WHERE tc.constraint_type = 'FOREIGN KEY'
    AND (
        tc.table_schema = 'common_company'
        OR ccu.table_schema = 'common_company'
    )
    AND (
        tc.table_name LIKE '%company%'
        OR ccu.table_name = 'common_company'
    )
ORDER BY tc.table_schema, tc.table_name;

-- 10. INDEX'LER
-- ============================================================================
SELECT
    schemaname,
    tablename,
    indexname,
    indexdef
FROM pg_indexes
WHERE schemaname = 'common_company'
AND tablename = 'common_company'
ORDER BY indexname;

-- 11. MIGRATION DURUMU
-- ============================================================================
SELECT 
    installed_rank,
    version,
    description,
    type,
    installed_on,
    success
FROM flyway_schema_history
WHERE description ILIKE '%company%'
ORDER BY installed_rank;

-- 12. SEQUENCE'LAR
-- ============================================================================
SELECT
    sequence_schema,
    sequence_name,
    start_value,
    minimum_value,
    maximum_value,
    increment
FROM information_schema.sequences
WHERE sequence_schema = 'common_company'
ORDER BY sequence_name;

-- 13. KULLANICI İZİNLERİ
-- ============================================================================
SELECT
    grantee,
    table_schema,
    table_name,
    privilege_type
FROM information_schema.role_table_grants
WHERE table_schema = 'common_company'
AND table_name = 'common_company'
ORDER BY grantee, privilege_type;

-- 14. TABLO BOYUTLARI VE İSTATİSTİKLER
-- ============================================================================
SELECT
    schemaname,
    tablename,
    pg_size_pretty(pg_total_relation_size(schemaname||'.'||tablename)) AS total_size,
    pg_size_pretty(pg_relation_size(schemaname||'.'||tablename)) AS table_size,
    pg_size_pretty(pg_indexes_size(schemaname||'.'||tablename)) AS indexes_size,
    (SELECT n_live_tup FROM pg_stat_user_tables WHERE schemaname||'.'||relname = schemaname||'.'||tablename) AS row_count
FROM pg_tables
WHERE schemaname = 'common_company'
ORDER BY pg_total_relation_size(schemaname||'.'||tablename) DESC;

-- 15. CONSTRAINT'LER
-- ============================================================================
SELECT
    tc.table_schema,
    tc.table_name,
    tc.constraint_name,
    tc.constraint_type,
    cc.check_clause
FROM information_schema.table_constraints tc
LEFT JOIN information_schema.check_constraints cc
    ON tc.constraint_name = cc.constraint_name
    AND tc.table_schema = cc.constraint_schema
WHERE tc.table_schema = 'common_company'
AND tc.table_name = 'common_company'
ORDER BY tc.constraint_type, tc.constraint_name;

-- ============================================================================
-- HIZLI DOĞRULAMA (TEK SORGU)
-- ============================================================================
SELECT 
    'Schema' AS check_item,
    CASE WHEN EXISTS(SELECT 1 FROM information_schema.schemata WHERE schema_name = 'common_company')
        THEN '✅ Var' ELSE '❌ Yok' END AS status
UNION ALL
SELECT 
    'Table: common_company',
    CASE WHEN EXISTS(SELECT 1 FROM information_schema.tables 
                     WHERE table_schema = 'common_company' AND table_name = 'common_company')
        THEN '✅ Var' ELSE '❌ Yok' END
UNION ALL
SELECT 
    'Table: common_department',
    CASE WHEN EXISTS(SELECT 1 FROM information_schema.tables 
                     WHERE table_schema = 'common_company' AND table_name = 'common_department')
        THEN '✅ Var' ELSE '❌ Yok' END
UNION ALL
SELECT 
    'Table: common_subscription',
    CASE WHEN EXISTS(SELECT 1 FROM information_schema.tables 
                     WHERE table_schema = 'common_company' AND table_name = 'common_subscription')
        THEN '✅ Var' ELSE '❌ Yok' END
UNION ALL
SELECT 
    'Records in common_company',
    CASE WHEN (SELECT COUNT(*) FROM common_company.common_company) > 0
        THEN '✅ ' || (SELECT COUNT(*)::text FROM common_company.common_company) || ' kayıt'
        ELSE '⚠️ Tablo boş' END;

