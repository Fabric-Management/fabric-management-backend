package com.fabricmanagement.platform.subscription.app;

import java.util.List;

public final class JobTitleSeedData {

  public record Preset(
      String code, String name, String description, String roleCode, String departmentCode) {}

  public static final List<Preset> ALL_PRESETS =
      List.of(
          // ── 1. Fiber & Yarn (Spinning) ──
          new Preset(
              "SPIN_MGR",
              "Spinning Mill Manager",
              "Manages overall spinning mill operations",
              "MANAGER",
              "YARN"),
          new Preset(
              "SPIN_SUPER",
              "Spinning Supervisor",
              "Manages shift and production line",
              "SUPERVISOR",
              "YARN"),
          new Preset(
              "BLOW_OP",
              "Blow Room Operator",
              "Operates fiber blending and cleaning line",
              "WORKER",
              "YARN"),
          new Preset("CARD_OP", "Carding Operator", "Operates carding machines", "WORKER", "YARN"),
          new Preset(
              "SPIN_OP",
              "Spinning Operator",
              "Operates Ring or Open-End spinning frames",
              "WORKER",
              "YARN"),
          new Preset("WIND_OP", "Winding Operator", "Operates winding machines", "WORKER", "YARN"),

          // ── 2. Fabric Formation (Knitting & Weaving) ──
          new Preset(
              "WEAV_MGR",
              "Weaving Manager",
              "Manages weaving production department",
              "MANAGER",
              "WEAVING"),
          new Preset(
              "KNIT_MGR",
              "Knitting Manager",
              "Manages knitting production department",
              "MANAGER",
              "KNITTING"),
          new Preset(
              "WEAV_SUPER",
              "Weaving Supervisor",
              "Supervises weaving shifts and quality",
              "SUPERVISOR",
              "WEAVING"),
          new Preset(
              "KNIT_SUPER",
              "Knitting Supervisor",
              "Supervises knitting shifts and quality",
              "SUPERVISOR",
              "KNITTING"),
          new Preset(
              "WARP_OP",
              "Warping Operator",
              "Prepares warp beams for weaving",
              "WORKER",
              "WEAVING"),
          new Preset(
              "WEAVER",
              "Loom Operator",
              "Operates and monitors weaving looms",
              "WORKER",
              "WEAVING"),
          new Preset(
              "KNITTER",
              "Knitting Operator",
              "Operates and monitors knitting machines",
              "WORKER",
              "KNITTING"),

          // ── 3. Dyeing & Finishing ──
          new Preset(
              "DYE_MGR",
              "Dyehouse Manager",
              "Manages wet processing facility",
              "MANAGER",
              "DYEING"),
          new Preset(
              "FINISH_MGR",
              "Finishing Manager",
              "Manages dry finishing processes",
              "MANAGER",
              "DYEING"),
          new Preset(
              "DYE_SUPER", "Dyeing Supervisor", "Supervises dyeing shifts", "SUPERVISOR", "DYEING"),
          new Preset(
              "CHEMIST",
              "Textile Chemist",
              "Optimizes chemical processes and formulas",
              "WORKER",
              "DYEING"),
          new Preset(
              "DYE_MIXER", "Dye Mixer", "Prepares dye and chemical solutions", "WORKER", "DYEING"),
          new Preset(
              "JET_OP",
              "Jet Dyeing Operator",
              "Operates HT fabric dyeing machines",
              "WORKER",
              "DYEING"),
          new Preset(
              "STENTER_OP",
              "Stenter Operator",
              "Operates drying and heat-setting machines",
              "WORKER",
              "DYEING"),
          new Preset(
              "PRINT_OP",
              "Printing Operator",
              "Operates rotary or digital printing machines",
              "WORKER",
              "DYEING"),

          // ── 4. Garment Manufacturing ──
          new Preset(
              "GARM_MGR",
              "Garment Factory Manager",
              "Manages cutting, sewing, and packing",
              "MANAGER",
              "GARMENT"),
          new Preset(
              "LINE_MGR",
              "Production Line Manager",
              "Supervises a specific sewing line",
              "SUPERVISOR",
              "GARMENT"),
          new Preset(
              "PATTERN",
              "Pattern Maker",
              "Creates patterns and markers (CAD)",
              "WORKER",
              "GARMENT"),
          new Preset(
              "CUT_SUPER",
              "Cutting Supervisor",
              "Supervises fabric spreading and cutting",
              "SUPERVISOR",
              "GARMENT"),
          new Preset(
              "SEW_OP",
              "Sewing Machine Operator",
              "Operates industrial sewing machines",
              "WORKER",
              "GARMENT"),
          new Preset(
              "IRON_OP",
              "Finishing Presser",
              "Handles final ironing and garment shaping",
              "WORKER",
              "GARMENT"),
          new Preset(
              "PACK_OP",
              "Packing Operator",
              "Handles labeling, folding, and packing",
              "WORKER",
              "GARMENT"),

          // ── 5. Quality Control & Laboratory ──
          new Preset(
              "QA_MGR",
              "QA Manager",
              "Manages quality standards and approvals",
              "MANAGER",
              "QUALITY"),
          new Preset(
              "COLORIST",
              "Colorist",
              "Handles color matching and recipe approval",
              "WORKER",
              "QUALITY"),
          new Preset(
              "LAB_TECH",
              "Lab Technician",
              "Performs physical testing (fastness, shrinkage)",
              "WORKER",
              "QUALITY"),
          new Preset(
              "YARN_QC",
              "Yarn QC Inspector",
              "Checks yarn quality (Uster, strength)",
              "WORKER",
              "QUALITY"),
          new Preset(
              "FABRIC_QC",
              "Fabric QC Inspector",
              "Performs 4-point fabric inspection",
              "WORKER",
              "QUALITY"),
          new Preset(
              "GARM_QC",
              "Garment QC Inspector",
              "Checks sewing defects and measurements",
              "WORKER",
              "QUALITY"),

          // ── 6. Logistics & Supply Chain ──
          new Preset(
              "SC_MGR",
              "Supply Chain Manager",
              "Manages end-to-end product flow",
              "MANAGER",
              "WAREHOUSE"),
          new Preset(
              "WH_MGR",
              "Warehouse Manager",
              "Manages warehouse operations and inventory",
              "MANAGER",
              "WAREHOUSE"),
          new Preset(
              "YARN_WH",
              "Yarn Warehouse Clerk",
              "Handles yarn receiving and dispatch",
              "WORKER",
              "WAREHOUSE"),
          new Preset(
              "FAB_WH",
              "Fabric Warehouse Clerk",
              "Handles greige and finished fabric inventory",
              "WORKER",
              "WAREHOUSE"),
          new Preset(
              "CHEM_WH",
              "Chemical Warehouse Clerk",
              "Handles dyes and hazardous chemicals",
              "WORKER",
              "WAREHOUSE"),
          new Preset(
              "LOG_COORD",
              "Logistics Coordinator",
              "Coordinates customs, freight, and export",
              "WORKER",
              "SHIPPING"),
          new Preset(
              "DISPATCHER",
              "Dispatcher",
              "Coordinates truck loading and packing lists",
              "WORKER",
              "SHIPPING"),

          // ── 7. Procurement & Sourcing ──
          new Preset(
              "PROC_MGR",
              "Procurement Manager",
              "Manages strategic sourcing and purchasing",
              "MANAGER",
              "PROCUREMENT"),
          new Preset(
              "YARN_BUYER",
              "Yarn & Fiber Buyer",
              "Purchases raw products",
              "WORKER",
              "PROCUREMENT"),
          new Preset(
              "CHEM_BUYER",
              "Chemical Buyer",
              "Purchases dyes and auxiliary chemicals",
              "WORKER",
              "PROCUREMENT"),
          new Preset(
              "SOURCER",
              "Sourcing Specialist",
              "Manages outsourced production (subcontractors)",
              "WORKER",
              "PROCUREMENT"),

          // ── 8. Sales & Merchandising ──
          new Preset(
              "SALES_DIR", "Sales Director", "Leads global sales strategy", "MANAGER", "SALES"),
          new Preset(
              "SALES_REP",
              "Sales Representative",
              "Handles customer orders and quotations",
              "WORKER",
              "SALES"),
          new Preset(
              "SR_MERCH",
              "Senior Merchandiser",
              "Manages key accounts and order lifecycle",
              "SUPERVISOR",
              "SALES"),
          new Preset(
              "MERCH", "Merchandiser", "Tracks production and sampling orders", "WORKER", "SALES"),
          new Preset(
              "SHOWROOM",
              "Showroom Coordinator",
              "Manages physical sample display and swatches",
              "WORKER",
              "SALES"),

          // ── 9. Design, R&D & Planning ──
          new Preset(
              "PLAN_MGR",
              "Planning Manager",
              "Manages capacity and lead times",
              "MANAGER",
              "PLANNING"),
          new Preset(
              "PLANNER",
              "Production Planner",
              "Schedules machines and work orders",
              "WORKER",
              "PLANNING"),
          new Preset("RD_MGR", "R&D Manager", "Manages new product development", "MANAGER", "RD"),
          new Preset(
              "FAB_DESIGN",
              "Fabric Designer",
              "Designs Dobby/Jacquard patterns (CAD)",
              "WORKER",
              "RD"),
          new Preset(
              "TECHNOLOG",
              "Fabric Technologist",
              "Defines fabric specifications and routes",
              "WORKER",
              "RD"),
          new Preset(
              "SAMPLE_MKR",
              "Sample Maker",
              "Produces physical samples for clients",
              "WORKER",
              "RD"));

  private JobTitleSeedData() {}
}
