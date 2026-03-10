package com.fabricmanagement.production.masterdata.fiber.infra.repository;

import com.fabricmanagement.production.masterdata.fiber.domain.FiberAttributeLink;
import com.fabricmanagement.production.masterdata.fiber.domain.FiberAttributeLinkId;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FiberAttributeLinkRepository
    extends JpaRepository<FiberAttributeLink, FiberAttributeLinkId> {

  List<FiberAttributeLink> findByFiberId(UUID fiberId);

  void deleteByFiberId(UUID fiberId);
}
