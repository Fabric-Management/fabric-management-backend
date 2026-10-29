package com.fabricmanagement.product.fabric.app.bootstrap;

import static com.fabricmanagement.product.fabric.domain.vocabulary.FabricFinishingCategory.CHEMICAL_FINISH;
import static com.fabricmanagement.product.fabric.domain.vocabulary.FabricFinishingCategory.COATING;
import static com.fabricmanagement.product.fabric.domain.vocabulary.FabricFinishingCategory.DYEING;
import static com.fabricmanagement.product.fabric.domain.vocabulary.FabricFinishingCategory.MECHANICAL_FINISH;
import static com.fabricmanagement.product.fabric.domain.vocabulary.FabricFinishingCategory.PRETREATMENT;
import static com.fabricmanagement.product.fabric.domain.vocabulary.FabricFinishingCategory.PRINTING;
import static com.fabricmanagement.product.fabric.domain.vocabulary.FinishingChargeUnit.KG;
import static com.fabricmanagement.product.fabric.domain.vocabulary.FinishingChargeUnit.M;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.product.fabric.domain.pricing.FabricFinishingPriceClass;
import com.fabricmanagement.product.fabric.domain.reference.FabricConstruction;
import com.fabricmanagement.product.fabric.domain.reference.FabricFinishingProcess;
import com.fabricmanagement.product.fabric.domain.vocabulary.FabricConstructionFamily;
import com.fabricmanagement.product.fabric.domain.vocabulary.FabricFinishingCategory;
import com.fabricmanagement.product.fabric.domain.vocabulary.FinishingChargeUnit;
import com.fabricmanagement.product.fabric.infra.repository.FabricConstructionRepository;
import com.fabricmanagement.product.fabric.infra.repository.FabricFinishingPriceClassRepository;
import com.fabricmanagement.product.fabric.infra.repository.FabricFinishingProcessRepository;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The constructions (FABRIC-ARCHITECTURE F3), finishing processes (FABRIC-3a) and finishing price
 * classes (FABRIC-3c) FabricOS provides, seeded into the template tenant and copied from there to
 * every tenant as system-defined rows a tenant may rename but not remove. British English names;
 * Turkish is a translation, never the source (root AGENTS.md).
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

  /** One finishing process FabricOS ships. */
  public record FinishingSeed(
      String code,
      String name,
      String description,
      int displayOrder,
      FabricFinishingCategory category,
      FinishingChargeUnit chargeUnit) {}

  private static final List<FinishingSeed> FINISHING_PROCESSES =
      List.of(
          finishing("SINGEING", "Singeing", "Burns off surface fibres.", 1, PRETREATMENT, M),
          finishing("DESIZING", "Desizing", "Removes the warp size.", 2, PRETREATMENT, KG),
          finishing("SCOURING", "Scouring", "Removes oils, waxes and dirt.", 3, PRETREATMENT, KG),
          finishing("BLEACHING", "Bleaching", "Whitens before dyeing.", 4, PRETREATMENT, KG),
          finishing(
              "MERCERISING",
              "Mercerising",
              "Caustic treatment for lustre and dye uptake.",
              5,
              PRETREATMENT,
              M),
          finishing(
              "HEAT_SETTING",
              "Heat setting",
              "Stabilises synthetic and elastane fabrics.",
              6,
              PRETREATMENT,
              M),
          finishing("PIECE_DYEING", "Piece dyeing", "Batch dyeing of the fabric.", 11, DYEING, KG),
          finishing(
              "CONTINUOUS_DYEING",
              "Continuous dyeing",
              "Pad dyeing on a continuous range.",
              12,
              DYEING,
              M),
          finishing("PIGMENT_PRINTING", "Pigment printing", "Pigment and binder.", 21, PRINTING, M),
          finishing(
              "REACTIVE_PRINTING",
              "Reactive printing",
              "Reactive dyes, steamed and washed.",
              22,
              PRINTING,
              M),
          finishing("DIGITAL_PRINTING", "Digital printing", "Inkjet printing.", 23, PRINTING, M),
          finishing("SOFTENING", "Softening", "Softer hand.", 31, CHEMICAL_FINISH, M),
          finishing(
              "WATER_REPELLENT",
              "Water-repellent finish",
              "Water beads off the surface.",
              32,
              CHEMICAL_FINISH,
              M),
          finishing(
              "EASY_CARE",
              "Easy-care finish",
              "Resin finish against creasing.",
              33,
              CHEMICAL_FINISH,
              M),
          finishing(
              "FLAME_RETARDANT",
              "Flame-retardant finish",
              "Slows the spread of flame.",
              34,
              CHEMICAL_FINISH,
              M),
          finishing(
              "ANTIBACTERIAL",
              "Antibacterial finish",
              "Inhibits bacterial growth.",
              35,
              CHEMICAL_FINISH,
              M),
          finishing(
              "SANFORISING",
              "Sanforising",
              "Compressive shrinkage control.",
              41,
              MECHANICAL_FINISH,
              M),
          finishing(
              "RAISING", "Raising", "Brushes up a nap from the surface.", 42, MECHANICAL_FINISH, M),
          finishing(
              "PEACHING", "Peaching", "Sanded, peach-skin surface.", 43, MECHANICAL_FINISH, M),
          finishing(
              "CALENDERING",
              "Calendering",
              "Pressed between rollers for a smooth, flat face.",
              44,
              MECHANICAL_FINISH,
              M),
          finishing(
              "COMPACTING",
              "Compacting",
              "Shrinkage control for knitted fabric.",
              45,
              MECHANICAL_FINISH,
              M),
          finishing(
              "STENTERING",
              "Stentering",
              "Sets width and dries on the stenter.",
              46,
              MECHANICAL_FINISH,
              M),
          finishing("COATING", "Coating", "A coated layer, such as PU or PVC.", 51, COATING, M),
          finishing("LAMINATING", "Laminating", "A bonded film or fabric.", 52, COATING, M));

  /** One finishing price class FabricOS ships; a tenant renames them and adds its own. */
  public record PriceClassSeed(String code, String name, String description, int displayOrder) {}

  private static final List<PriceClassSeed> PRICE_CLASSES =
      List.of(
          new PriceClassSeed("LIGHT", "Light", "Pale shades, a low dye percentage.", 1),
          new PriceClassSeed("MEDIUM", "Medium", "Mid shades.", 2),
          new PriceClassSeed("DARK", "Dark", "Deep shades, a high dye percentage.", 3),
          new PriceClassSeed("EXTRA_DARK", "Extra dark", "The deepest shades.", 4),
          new PriceClassSeed("BLACK", "Black", "Priced as a line of its own.", 5),
          new PriceClassSeed("ROYAL", "Royal blue", "Priced as a line of its own.", 6),
          new PriceClassSeed("TURQUOISE", "Turquoise", "Priced as a line of its own.", 7));

  private final FabricConstructionRepository repository;
  private final FabricFinishingProcessRepository finishingRepository;
  private final FabricFinishingPriceClassRepository priceClassRepository;
  private final TransactionTemplate transactionTemplate;

  public static List<ConstructionSeed> constructions() {
    return CONSTRUCTIONS;
  }

  public static List<FinishingSeed> finishingProcesses() {
    return FINISHING_PROCESSES;
  }

  public static List<PriceClassSeed> priceClasses() {
    return PRICE_CLASSES;
  }

  /**
   * Inserts the missing system constructions, finishing processes and price classes of a tenant;
   * returns how many rows were inserted.
   */
  public int seed(UUID tenantId) {
    return seedConstructions(tenantId)
        + seedFinishingProcesses(tenantId)
        + seedPriceClasses(tenantId);
  }

  private int seedConstructions(UUID tenantId) {
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

  private int seedFinishingProcesses(UUID tenantId) {
    return TenantContext.executeInTenantContext(
        tenantId,
        () ->
            transactionTemplate.execute(
                status -> {
                  List<FabricFinishingProcess> missing =
                      FINISHING_PROCESSES.stream()
                          .filter(
                              seed ->
                                  !finishingRepository.existsByTenantIdAndCode(
                                      tenantId, seed.code()))
                          .map(
                              seed ->
                                  FabricFinishingProcess.defineSystem(
                                      tenantId,
                                      seed.code(),
                                      seed.name(),
                                      seed.description(),
                                      seed.displayOrder(),
                                      seed.category(),
                                      seed.chargeUnit()))
                          .toList();
                  finishingRepository.saveAll(missing);
                  if (!missing.isEmpty()) {
                    log.info("Seeded {} finishing processes for {}", missing.size(), tenantId);
                  }
                  return missing.size();
                }));
  }

  private int seedPriceClasses(UUID tenantId) {
    return TenantContext.executeInTenantContext(
        tenantId,
        () ->
            transactionTemplate.execute(
                status -> {
                  List<FabricFinishingPriceClass> missing =
                      PRICE_CLASSES.stream()
                          .filter(
                              seed ->
                                  !priceClassRepository.existsByTenantIdAndCode(
                                      tenantId, seed.code()))
                          .map(
                              seed ->
                                  FabricFinishingPriceClass.defineSystem(
                                      tenantId,
                                      seed.code(),
                                      seed.name(),
                                      seed.description(),
                                      seed.displayOrder()))
                          .toList();
                  priceClassRepository.saveAll(missing);
                  if (!missing.isEmpty()) {
                    log.info("Seeded {} finishing price classes for {}", missing.size(), tenantId);
                  }
                  return missing.size();
                }));
  }

  private static FinishingSeed finishing(
      String code,
      String name,
      String description,
      int order,
      FabricFinishingCategory category,
      FinishingChargeUnit chargeUnit) {
    return new FinishingSeed(code, name, description, order, category, chargeUnit);
  }

  private static ConstructionSeed seed(
      String code, String name, String description, int order, FabricConstructionFamily family) {
    return new ConstructionSeed(code, name, description, order, family);
  }
}
