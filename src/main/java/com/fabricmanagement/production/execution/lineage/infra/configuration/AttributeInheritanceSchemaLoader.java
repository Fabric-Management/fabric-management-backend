package com.fabricmanagement.production.execution.lineage.infra.configuration;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.production.execution.lineage.domain.rule.AttributeInheritanceSchema;
import com.fabricmanagement.production.execution.lineage.domain.rule.InheritanceRule;
import com.fabricmanagement.production.execution.lineage.infra.persistence.AttributeInheritanceSchemaRepository;
import com.fabricmanagement.production.masterdata.product.domain.ProductType;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

/**
 * Loads Attribute Inheritance Rule schemas from static JSON files on the classpath and exposes them
 * by source→target product type for the Metadata-Driven Attribute Inheritance Engine.
 *
 * <p>At startup, scans {@code classpath:inheritance-rules/*.json}, deserializes each file into an
 * {@link AttributeInheritanceSchema}, and caches them in an immutable map keyed by {@code
 * SOURCE_TO_TARGET} (e.g. {@code "FIBER_TO_YARN"}).
 *
 * <p>If two or more files resolve to the same source→target key (e.g. {@code fiber-to-yarn.json}
 * and {@code fiber-to-yarn-v2.json} both produce {@code FIBER_TO_YARN}), startup fails with an
 * {@link IllegalStateException}. Duplicate keys indicate a configuration error and must not be
 * silently overwritten. If a single file fails to parse, an error is logged and that schema is
 * omitted; other schemas still load.
 *
 * <p>Phase 5 Implemented: This loader now acts as a dual-layer provider. First, it queries the
 * {@code inheritance_rule_schema} database table for tenant-specific rule overrides via the
 * repository. If no tenant-specific rule exists, it gracefully falls back to the in-memory
 * classpath defaults.
 */
@Component
public class AttributeInheritanceSchemaLoader {

  private static final String RULES_LOCATION = "classpath:inheritance-rules/*.json";
  private static final Logger log = LoggerFactory.getLogger(AttributeInheritanceSchemaLoader.class);
  private static final long CACHE_TTL_MS = 10 * 60 * 1000L; // 10 minutes

  private final ResourcePatternResolver resourceResolver;
  private final ObjectMapper objectMapper;

  /**
   * Immutable cache of loaded schemas. Key format: {@code SOURCE_TO_TARGET} (e.g. FIBER_TO_YARN).
   * Populated at startup; never modified afterwards. Wrapped in unmodifiableMap for thread safety.
   */
  private volatile Map<String, AttributeInheritanceSchema> cache = Collections.emptyMap();

  private final AttributeInheritanceSchemaRepository repository;

  /**
   * Tenant-level DB schema cache to avoid per-call DB queries on high-frequency production paths.
   */
  private final ConcurrentHashMap<String, CachedSchema> dbSchemaCache = new ConcurrentHashMap<>();

  public AttributeInheritanceSchemaLoader(
      ResourcePatternResolver resourceResolver,
      ObjectMapper objectMapper,
      AttributeInheritanceSchemaRepository repository) {
    this.resourceResolver = resourceResolver;
    this.objectMapper = objectMapper;
    this.repository = repository;
  }

  /**
   * Scans the classpath for JSON schema files under {@code inheritance-rules/}, deserializes each
   * into an {@link AttributeInheritanceSchema}, and builds the internal cache. Throws {@link
   * IllegalStateException} if two files resolve to the same source→target key (duplicate rule).
   * Single-file parse failures are logged and that schema is skipped.
   */
  @PostConstruct
  public void loadSchemas() {
    Map<String, AttributeInheritanceSchema> loaded = new HashMap<>();
    Map<String, String> keyToFilename = new HashMap<>();
    try {
      Resource[] resources = resourceResolver.getResources(RULES_LOCATION);
      log.info(
          "Loading attribute inheritance schemas from {} ({} file(s) found)",
          RULES_LOCATION,
          resources.length);

      for (Resource resource : resources) {
        loadOne(resource)
            .ifPresent(
                schema -> {
                  String key = cacheKey(schema.sourceType(), schema.targetType());
                  String filename = resource.getFilename();
                  if (loaded.containsKey(key)) {
                    String previousFile = keyToFilename.get(key);
                    throw new IllegalStateException(
                        "Duplicate attribute inheritance schema key '%s': both '%s' and '%s' define %s -> %s. "
                            + "Remove or rename one of the files so each source→target pair has exactly one schema."
                                .formatted(
                                    key,
                                    previousFile,
                                    filename,
                                    schema.sourceType(),
                                    schema.targetType()));
                  }
                  loaded.put(key, schema);
                  keyToFilename.put(key, filename);
                });
      }

      this.cache = Collections.unmodifiableMap(loaded);
      log.info("Attribute inheritance schema loading complete. Cached {} schema(s).", cache.size());
    } catch (IllegalStateException e) {
      throw e;
    } catch (Exception e) {
      log.error(
          "Failed to scan inheritance rules location: {}. No schemas loaded.", RULES_LOCATION, e);
      this.cache = Collections.emptyMap();
    }
  }

  /**
   * Returns the schema for the given source→target product type pair. First checks the database for
   * tenant-specific overrides (loaded via repository). If not found, falls back to the
   * classpath-loaded generic schemas.
   *
   * @param source product type of the consumed parent batch(es) (e.g. FIBER)
   * @param target product type of the batch being produced (e.g. YARN)
   * @return the schema, or empty if no matching JSON file or DB record was found
   */
  public Optional<AttributeInheritanceSchema> getSchema(ProductType source, ProductType target) {
    UUID tenantId = TenantContext.requireTenantId();

    if (tenantId != null) {
      String dbKey = tenantId + "_" + cacheKey(source, target);
      CachedSchema cached = dbSchemaCache.get(dbKey);

      if (cached != null && !cached.isExpired()) {
        return cached.schema;
      }

      var dbSchema =
          repository.findByTenantIdAndSourceTypeAndTargetTypeAndIsActiveTrue(
              tenantId, source, target);

      if (dbSchema.isPresent()) {
        try {
          List<InheritanceRule> rules =
              objectMapper.readValue(
                  dbSchema.get().getRulesJson(), new TypeReference<List<InheritanceRule>>() {});
          Optional<AttributeInheritanceSchema> result =
              Optional.of(new AttributeInheritanceSchema(source, target, rules));
          dbSchemaCache.put(dbKey, new CachedSchema(result));
          return result;
        } catch (Exception e) {
          log.error(
              "Failed to parse tenant {} inheritance rule from DB for {} -> {}",
              tenantId,
              source,
              target,
              e);
        }
      } else {
        // Cache the 'not found' to avoid repeated DB queries
        dbSchemaCache.put(dbKey, new CachedSchema(Optional.empty()));
      }
    }

    String key = cacheKey(source, target);
    return Optional.ofNullable(cache.get(key));
  }

  private static String cacheKey(ProductType source, ProductType target) {
    return source.name() + "_TO_" + target.name();
  }

  private Optional<AttributeInheritanceSchema> loadOne(Resource resource) {
    String filename = resource.getFilename();
    try (InputStream in = resource.getInputStream()) {
      AttributeInheritanceSchema schema =
          objectMapper.readValue(in, AttributeInheritanceSchema.class);
      log.debug(
          "Loaded inheritance schema from '{}': {} -> {}",
          filename,
          schema.sourceType(),
          schema.targetType());
      return Optional.of(schema);
    } catch (Exception e) {
      log.error(
          "Failed to load attribute inheritance schema from '{}'. Schema skipped.", filename, e);
      return Optional.empty();
    }
  }

  /** Time-boxed cache entry for DB schema lookups. */
  private class CachedSchema {
    final Optional<AttributeInheritanceSchema> schema;
    final long createdAt;

    CachedSchema(Optional<AttributeInheritanceSchema> schema) {
      this.schema = schema;
      this.createdAt = System.currentTimeMillis();
    }

    boolean isExpired() {
      return System.currentTimeMillis() - createdAt > CACHE_TTL_MS;
    }
  }
}
