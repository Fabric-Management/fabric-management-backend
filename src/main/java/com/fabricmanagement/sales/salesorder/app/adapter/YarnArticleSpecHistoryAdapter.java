package com.fabricmanagement.sales.salesorder.app.adapter;

import com.fabricmanagement.product.yarn.app.YarnArticleSpecHistoryQuery;
import com.fabricmanagement.product.yarn.dto.YarnArticleHistorySnapshotDto;
import com.fabricmanagement.sales.salesorder.domain.port.YarnArticleSpecHistoryPort;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Sales application adapter over product's public history query. */
@Component
@RequiredArgsConstructor
public class YarnArticleSpecHistoryAdapter implements YarnArticleSpecHistoryPort {

  private final YarnArticleSpecHistoryQuery yarnArticleSpecHistoryQuery;

  @Override
  public Snapshot historyVersion(UUID articleId, int specificationVersion) {
    YarnArticleHistorySnapshotDto source =
        yarnArticleSpecHistoryQuery.historyVersion(articleId, specificationVersion);
    return new Snapshot(
        articleId, source.specVersion(), source.specAfter(), source.actorId(), source.timestamp());
  }
}
