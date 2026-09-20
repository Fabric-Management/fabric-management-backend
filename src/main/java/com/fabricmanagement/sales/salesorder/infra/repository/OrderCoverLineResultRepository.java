package com.fabricmanagement.sales.salesorder.infra.repository;

import com.fabricmanagement.sales.salesorder.domain.OrderCoverLineResult;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderCoverLineResultRepository extends JpaRepository<OrderCoverLineResult, UUID> {
  List<OrderCoverLineResult> findAllByResultIdOrderByLineId(UUID resultId);
}
