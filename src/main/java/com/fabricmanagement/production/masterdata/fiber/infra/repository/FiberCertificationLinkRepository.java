package com.fabricmanagement.production.masterdata.fiber.infra.repository;

import com.fabricmanagement.production.masterdata.fiber.domain.FiberCertificationLink;
import com.fabricmanagement.production.masterdata.fiber.domain.FiberCertificationLinkId;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FiberCertificationLinkRepository
    extends JpaRepository<FiberCertificationLink, FiberCertificationLinkId> {

  List<FiberCertificationLink> findByFiberId(UUID fiberId);

  void deleteByFiberId(UUID fiberId);
}
