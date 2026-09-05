CREATE INDEX idx_yarn_article_tenant_status
    ON production.prod_yarn_article (tenant_id, status);
