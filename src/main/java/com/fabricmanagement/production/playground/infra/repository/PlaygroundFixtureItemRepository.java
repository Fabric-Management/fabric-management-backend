package com.fabricmanagement.production.playground.infra.repository;

import com.fabricmanagement.production.playground.domain.PlaygroundFixtureItem;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PlaygroundFixtureItemRepository
    extends JpaRepository<PlaygroundFixtureItem, UUID> {

  Optional<PlaygroundFixtureItem> findByTenantIdAndFixtureKey(UUID tenantId, String fixtureKey);

  List<PlaygroundFixtureItem> findByTenantId(UUID tenantId);
}
