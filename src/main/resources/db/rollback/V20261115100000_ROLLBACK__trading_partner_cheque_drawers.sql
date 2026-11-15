-- Development-only destructive rollback. Cheques keep their drawer name snapshot; drawer_id
-- references (PAYMENT-PLAN-7 S2b) become dangling and must be cleared first in that slice.
DROP TABLE IF EXISTS common_company.trading_partner_cheque_drawer;
