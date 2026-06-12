-- Rename FASON partner_type to SUBCONTRACTOR in common_company.common_trading_partner
UPDATE common_company.common_trading_partner
   SET partner_type = 'SUBCONTRACTOR'
 WHERE partner_type = 'FASON';
