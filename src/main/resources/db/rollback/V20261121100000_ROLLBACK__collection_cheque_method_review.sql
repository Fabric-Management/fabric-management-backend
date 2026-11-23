-- Development-only destructive rollback; recorded cheque offers are lost.
DROP TABLE IF EXISTS finance.collection_cheque_offer;
-- Retain the widened follow-up phase vocabulary: existing REVIEW_PAYMENT_METHOD rows stay valid.
