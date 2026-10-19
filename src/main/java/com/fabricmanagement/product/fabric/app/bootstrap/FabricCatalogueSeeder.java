package com.fabricmanagement.product.fabric.app.bootstrap;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.product.fabric.domain.reference.FabricConstruction;
import com.fabricmanagement.product.fabric.domain.vocabulary.FabricConstructionFamily;
import com.fabricmanagement.product.fabric.infra.repository.FabricConstructionRepository;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The constructions FabricOS provides (FABRIC-ARCHITECTURE F3), seeded into the template tenant and
 * copied from there to every tenant as system-defined rows a tenant may rename but not remove.
 * British English names; Turkish is a translation, never the source (root AGENTS.md).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FabricCatalogueSeeder {

  /** One construction FabricOS ships. */
  public record ConstructionSeed(
      String code,
      String name,
      String description,
      int displayOrder,
      FabricConstructionFamily family) {}

  private static final List<ConstructionSeed> CONSTRUCTIONS =
      List.of(
          seed("PLAIN", "Plain weave", "One over, one under.", 1, FabricConstructionFamily.PLAIN),
          seed(
              "POPLIN",
              "Poplin",
              "Plain weave with a fine rib.",
              2,
              FabricConstructionFamily.PLAIN),
          seed("CANVAS", "Canvas", "Heavy plain weave.", 3, FabricConstructionFamily.PLAIN),
          seed("TWILL_2_1", "2/1 twill", "Two over, one under.", 4, FabricConstructionFamily.TWILL),
          seed(
              "TWILL_3_1",
              "3/1 twill",
              "Three over, one under.",
              5,
              FabricConstructionFamily.TWILL),
          seed("DENIM", "Denim", "Warp-faced 3/1 twill.", 6, FabricConstructionFamily.TWILL),
          seed("GABARDINE", "Gabardine", "Steep, tight twill.", 7, FabricConstructionFamily.TWILL),
          seed("SATIN", "Satin", "Warp-faced satin.", 8, FabricConstructionFamily.SATIN),
          seed("SATEEN", "Sateen", "Weft-faced satin.", 9, FabricConstructionFamily.SATIN),
          seed("DOBBY", "Dobby", "Small woven pattern.", 10, FabricConstructionFamily.DOBBY),
          seed(
              "JACQUARD",
              "Jacquard",
              "Large woven pattern.",
              11,
              FabricConstructionFamily.JACQUARD),
          seed("LENO", "Leno", "Crossed warp ends.", 12, FabricConstructionFamily.LENO),
          seed("WOVEN_PILE", "Woven pile", "Velvet, corduroy.", 13, FabricConstructionFamily.PILE),
          seed(
              "SINGLE_JERSEY",
              "Single jersey",
              "Plain weft knit.",
              21,
              FabricConstructionFamily.SINGLE_JERSEY),
          seed(
              "RIB_1X1", "1x1 rib", "Alternating knit and purl.", 22, FabricConstructionFamily.RIB),
          seed("RIB_2X2", "2x2 rib", "Two knit, two purl.", 23, FabricConstructionFamily.RIB),
          seed(
              "INTERLOCK",
              "Interlock",
              "Double knit, smooth both sides.",
              24,
              FabricConstructionFamily.INTERLOCK),
          seed("PIQUE", "Pique", "Textured tuck knit.", 25, FabricConstructionFamily.PIQUE),
          seed("FLEECE", "Fleece", "Brushed loop-back knit.", 26, FabricConstructionFamily.FLEECE),
          seed(
              "KNITTED_TERRY",
              "Knitted terry",
              "Loop pile knit.",
              27,
              FabricConstructionFamily.TERRY),
          seed(
              "WARP_KNIT",
              "Warp knit",
              "Tricot and raschel.",
              28,
              FabricConstructionFamily.WARP_KNIT),
          seed("NONWOVEN", "Nonwoven", "Bonded fibre web.", 41, FabricConstructionFamily.NONWOVEN));

  private final FabricConstructionRepository repository;
  private final TransactionTemplate transactionTemplate;

  public static List<ConstructionSeed> constructions() {
    return CONSTRUCTIONS;
  }

  /** Inserts the missing system constructions of a tenant; returns how many were inserted. */
  public int seed(UUID tenantId) {
    return TenantContext.executeInTenantContext(
        tenantId,
        () ->
            transactionTemplate.execute(
                status -> {
                  Set<String> existing =
                      repository.findByTenantIdAndDeletedAtIsNull(tenantId).stream()
                          .map(FabricConstruction::getCode)
                          .collect(Collectors.toSet());
                  List<FabricConstruction> missing =
                      CONSTRUCTIONS.stream()
                          .filter(seed -> !existing.contains(seed.code()))
                          .filter(
                              seed -> !repository.existsByTenantIdAndCode(tenantId, seed.code()))
                          .map(
                              seed ->
                                  FabricConstruction.defineSystem(
                                      tenantId,
                                      seed.code(),
                                      seed.name(),
                                      seed.description(),
                                      seed.displayOrder(),
                                      seed.family()))
                          .toList();
                  repository.saveAll(missing);
                  if (!missing.isEmpty()) {
                    log.info("Seeded {} fabric constructions for {}", missing.size(), tenantId);
                  }
                  return missing.size();
                }));
  }

  private static ConstructionSeed seed(
      String code, String name, String description, int order, FabricConstructionFamily family) {
    return new ConstructionSeed(code, name, description, order, family);
  }
}
