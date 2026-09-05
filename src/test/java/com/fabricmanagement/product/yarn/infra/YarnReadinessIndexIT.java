package com.fabricmanagement.product.yarn.infra;

import static org.assertj.core.api.Assertions.assertThat;

import com.fabricmanagement.common.infrastructure.persistence.SystemTransactionExecutor;
import com.fabricmanagement.testsupport.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class YarnReadinessIndexIT extends AbstractIntegrationTest {

  @Autowired private SystemTransactionExecutor systemTransactions;

  @Test
  void readinessIndexesHaveTheExactPlainCompositeShapeAndLedgerIndexIsValid() {
    String article = indexDefinition("idx_yarn_article_tenant_status");
    String ledger = indexDefinition("idx_inv_txn_tenant_date");

    assertThat(normalize(article))
        .contains("on production.prod_yarn_article using btree (tenant_id, status)")
        .doesNotContain(" where ");
    assertThat(normalize(ledger))
        .contains(
            "on production.production_execution_inventory_transaction using btree (tenant_id, transaction_date)")
        .doesNotContain(" where ");
    Boolean ledgerIndexValid =
        systemTransactions.executeInTransaction(
            jdbc ->
                jdbc.queryForObject(
                    "SELECT indisvalid FROM pg_index "
                        + "WHERE indexrelid='production.idx_inv_txn_tenant_date'::regclass",
                    Boolean.class));

    assertThat(ledgerIndexValid).isTrue();
  }

  private String indexDefinition(String name) {
    return systemTransactions.executeInTransaction(
        jdbc ->
            jdbc.queryForObject(
                "SELECT indexdef FROM pg_indexes WHERE schemaname='production' AND indexname=?",
                String.class,
                name));
  }

  private String normalize(String value) {
    return value.toLowerCase().replaceAll("\\s+", " ");
  }
}
