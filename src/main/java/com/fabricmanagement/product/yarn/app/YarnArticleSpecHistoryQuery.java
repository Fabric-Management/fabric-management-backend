package com.fabricmanagement.product.yarn.app;

import com.fabricmanagement.product.yarn.dto.YarnArticleHistorySnapshotDto;
import java.util.UUID;

/** Product-owned read boundary for reconstructing an immutable yarn specification version. */
public interface YarnArticleSpecHistoryQuery {

  YarnArticleHistorySnapshotDto historyVersion(UUID articleId, int specificationVersion);
}
