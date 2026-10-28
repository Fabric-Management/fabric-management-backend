package com.fabricmanagement.finance.collection.infra.repository;

import com.fabricmanagement.finance.collection.domain.CollectionSourceObservation;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CollectionSourceObservationRepository
    extends JpaRepository<CollectionSourceObservation, UUID> {}
