-- [D7 FIX] DashboardWidget - DashboardConfig FK Migration
-- Mevcut Dashboard Widget kayıtlarının bozuk/yetim (dashboard_config tablosunda olmayan id) olması durumuna karşı
DELETE FROM flowboard.dashboard_widget 
WHERE dashboard_id NOT IN (SELECT id FROM flowboard.dashboard_config);

-- Foreign Key Constraints
ALTER TABLE flowboard.dashboard_widget
ADD CONSTRAINT fk_dashboard_widget_dashboard_id
FOREIGN KEY (dashboard_id) REFERENCES flowboard.dashboard_config(id) ON DELETE CASCADE;
