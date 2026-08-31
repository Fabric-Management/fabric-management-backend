CREATE INDEX IF NOT EXISTS idx_yarn_article_tenant_tex
    ON production.prod_yarn_article (tenant_id, resultant_linear_density_tex)
    WHERE resultant_linear_density_tex IS NOT NULL;
