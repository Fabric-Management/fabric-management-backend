package com.fabricmanagement.product.yarn.infra;

import static org.assertj.core.api.Assertions.assertThat;

import com.fabricmanagement.product.yarn.domain.backfill.YarnBackfillReconciliation;
import com.fabricmanagement.product.yarn.infra.repository.YarnBackfillReconciliationRepository;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

class YarnReconciliationRepositoryContractTest {

  @Test
  void listProjectionDoesNotSelectCandidatesAndFixesTheTotalOrder() throws Exception {
    String sql = query("findListPage").value().toLowerCase(Locale.ROOT);

    assertThat(sql)
        .contains("r.candidate_occurrence_count", "r.resolved_candidate::text")
        .contains("order by r.created_at asc, r.id asc")
        .doesNotContain("r.candidates");
    assertThat(
            Arrays.stream(YarnBackfillReconciliation.class.getDeclaredFields())
                .map(java.lang.reflect.Field::getName))
        .doesNotContain("candidateOccurrenceCount");
  }

  @Test
  void candidateStatementsUseTheByteaKeyOnlyWhereValuesAreCompared() throws Exception {
    String groupPage = query("findCandidateGroupPage").value().toLowerCase(Locale.ROOT);
    String groupTotal = query("countCandidateGroups").value().toLowerCase(Locale.ROOT);
    String identity = query("findCandidateIdentities").value().toLowerCase(Locale.ROOT);

    assertThat(groupPage)
        .contains("group by convert_to(candidate.elem ->> 'rawvalue', 'utf8')")
        .doesNotContain("group by candidate.elem ->>", "group by candidate.elem -> 'rawvalue'");
    assertThat(groupTotal)
        .contains("group by convert_to(candidate.elem ->> 'rawvalue', 'utf8')")
        .doesNotContain("count(distinct candidate.elem ->>", "candidate.elem -> 'rawvalue'");
    assertThat(identity)
        .contains("with ordinality", "candidate.ordinality in (:ordinals)")
        .doesNotContain("convert_to", "group by", "count(distinct");
  }

  @Test
  void mutationFinderIsPessimisticAndHasNoFetchJoin() throws Exception {
    Method method = method("findByTenantIdAndIdForUpdate");
    assertThat(method.getAnnotation(Lock.class).value())
        .isEqualTo(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
    assertThat(method.getAnnotation(Query.class).value().toLowerCase(Locale.ROOT))
        .doesNotContain("join fetch");
  }

  @Test
  void everyNativeTableReferenceIsSchemaQualified() throws Exception {
    for (String name :
        java.util.List.of(
            "findListPage",
            "findCandidateGroupPage",
            "findCandidateIdentities",
            "countCandidateGroups")) {
      String sql = query(name).value().toLowerCase(Locale.ROOT);
      assertThat(sql).as(name).doesNotContain("from prod_", "join prod_").contains("production.");
    }
  }

  private static Query query(String name) throws Exception {
    return method(name).getAnnotation(Query.class);
  }

  private static Method method(String name) throws Exception {
    return Arrays.stream(YarnBackfillReconciliationRepository.class.getDeclaredMethods())
        .filter(candidate -> candidate.getName().equals(name))
        .findFirst()
        .orElseThrow();
  }
}
