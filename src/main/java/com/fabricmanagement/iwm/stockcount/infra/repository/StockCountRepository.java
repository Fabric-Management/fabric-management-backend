package com.fabricmanagement.iwm.stockcount.infra.repository;

import com.fabricmanagement.iwm.stockcount.domain.StockCount;
import com.fabricmanagement.iwm.stockcount.domain.StockCountStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StockCountRepository extends JpaRepository<StockCount, UUID> {
  List<StockCount> findByLocationIdAndStatusAndDeletedAtIsNull(
      UUID locationId, StockCountStatus status);
}
