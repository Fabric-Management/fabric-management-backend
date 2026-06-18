package com.fabricmanagement.platform.subscription.app;

import com.fabricmanagement.platform.organization.domain.SystemDepartment;
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
              SystemDepartment.YARN.code()),
          new Preset(
              "SPIN_SUPER",
              "Spinning Supervisor",
              "Manages shift and production line",
              "SUPERVISOR",
              SystemDepartment.YARN.code()),
          new Preset(
              "BLOW_OP",
              "Blow Room Operator",
              "Operates fiber blending and cleaning line",
              "WORKER",
              SystemDepartment.YARN.code()),
          new Preset(
              "CARD_OP",
              "Carding Operator",
              "Operates carding machines",
              "WORKER",
              SystemDepartment.YARN.code()),
          new Preset(
              "SPIN_OP",
              "Spinning Operator",
              "Operates Ring or Open-End spinning frames",
              "WORKER",
              SystemDepartment.YARN.code()),
          new Preset(
              "WIND_OP",
              "Winding Operator",
              "Operates winding machines",
              "WORKER",
              SystemDepartment.YARN.code()),

          // ── 2. Fabric Formation (Knitting & Weaving) ──
          new Preset(
              "WEAV_MGR",
              "Weaving Manager",
              "Manages weaving production department",
              "MANAGER",
              SystemDepartment.WEAVING.code()),
          new Preset(
              "KNIT_MGR",
              "Knitting Manager",
              "Manages knitting production department",
              "MANAGER",
              SystemDepartment.KNITTING.code()),
          new Preset(
              "WEAV_SUPER",
              "Weaving Supervisor",
              "Supervises weaving shifts and quality",
              "SUPERVISOR",
              SystemDepartment.WEAVING.code()),
          new Preset(
              "KNIT_SUPER",
              "Knitting Supervisor",
              "Supervises knitting shifts and quality",
              "SUPERVISOR",
              SystemDepartment.KNITTING.code()),
          new Preset(
              "WARP_OP",
              "Warping Operator",
              "Prepares warp beams for weaving",
              "WORKER",
              SystemDepartment.WEAVING.code()),
          new Preset(
              "WEAVER",
              "Loom Operator",
              "Operates and monitors weaving looms",
              "WORKER",
              SystemDepartment.WEAVING.code()),
          new Preset(
              "KNITTER",
              "Knitting Operator",
              "Operates and monitors knitting machines",
              "WORKER",
              SystemDepartment.KNITTING.code()),

          // ── 3. Dyeing & Finishing ──
          new Preset(
              "DYE_MGR",
              "Dyehouse Manager",
              "Manages wet processing facility",
              "MANAGER",
              SystemDepartment.DYEING.code()),
          new Preset(
              "FINISH_MGR",
              "Finishing Manager",
              "Manages dry finishing processes",
              "MANAGER",
              SystemDepartment.DYEING.code()),
          new Preset(
              "DYE_SUPER",
              "Dyeing Supervisor",
              "Supervises dyeing shifts",
              "SUPERVISOR",
              SystemDepartment.DYEING.code()),
          new Preset(
              "CHEMIST",
              "Textile Chemist",
              "Optimizes chemical processes and formulas",
              "WORKER",
              SystemDepartment.DYEING.code()),
          new Preset(
              "DYE_MIXER",
              "Dye Mixer",
              "Prepares dye and chemical solutions",
              "WORKER",
              SystemDepartment.DYEING.code()),
          new Preset(
              "JET_OP",
              "Jet Dyeing Operator",
              "Operates HT fabric dyeing machines",
              "WORKER",
              SystemDepartment.DYEING.code()),
          new Preset(
              "STENTER_OP",
              "Stenter Operator",
              "Operates drying and heat-setting machines",
              "WORKER",
              SystemDepartment.DYEING.code()),
          new Preset(
              "PRINT_OP",
              "Printing Operator",
              "Operates rotary or digital printing machines",
              "WORKER",
              SystemDepartment.DYEING.code()),

          // ── 4. Garment Manufacturing ──
          new Preset(
              "GARM_MGR",
              "Garment Factory Manager",
              "Manages cutting, sewing, and packing",
              "MANAGER",
              SystemDepartment.GARMENT.code()),
          new Preset(
              "LINE_MGR",
              "Production Line Manager",
              "Supervises a specific sewing line",
              "SUPERVISOR",
              SystemDepartment.GARMENT.code()),
          new Preset(
              "PATTERN",
              "Pattern Maker",
              "Creates patterns and markers (CAD)",
              "WORKER",
              SystemDepartment.GARMENT.code()),
          new Preset(
              "CUT_SUPER",
              "Cutting Supervisor",
              "Supervises fabric spreading and cutting",
              "SUPERVISOR",
              SystemDepartment.GARMENT.code()),
          new Preset(
              "SEW_OP",
              "Sewing Machine Operator",
              "Operates industrial sewing machines",
              "WORKER",
              SystemDepartment.GARMENT.code()),
          new Preset(
              "IRON_OP",
              "Finishing Presser",
              "Handles final ironing and garment shaping",
              "WORKER",
              SystemDepartment.GARMENT.code()),
          new Preset(
              "PACK_OP",
              "Packing Operator",
              "Handles labeling, folding, and packing",
              "WORKER",
              SystemDepartment.GARMENT.code()),

          // ── 5. Quality Control & Laboratory ──
          new Preset(
              "QA_MGR",
              "QA Manager",
              "Manages quality standards and approvals",
              "MANAGER",
              SystemDepartment.QUALITY.code()),
          new Preset(
              "COLORIST",
              "Colorist",
              "Handles color matching and recipe approval",
              "WORKER",
              SystemDepartment.QUALITY.code()),
          new Preset(
              "LAB_TECH",
              "Lab Technician",
              "Performs physical testing (fastness, shrinkage)",
              "WORKER",
              SystemDepartment.QUALITY.code()),
          new Preset(
              "YARN_QC",
              "Yarn QC Inspector",
              "Checks yarn quality (Uster, strength)",
              "WORKER",
              SystemDepartment.QUALITY.code()),
          new Preset(
              "FABRIC_QC",
              "Fabric QC Inspector",
              "Performs 4-point fabric inspection",
              "WORKER",
              SystemDepartment.QUALITY.code()),
          new Preset(
              "GARM_QC",
              "Garment QC Inspector",
              "Checks sewing defects and measurements",
              "WORKER",
              SystemDepartment.QUALITY.code()),

          // ── 6. Logistics & Supply Chain ──
          new Preset(
              "SC_MGR",
              "Supply Chain Manager",
              "Manages end-to-end product flow",
              "MANAGER",
              SystemDepartment.WAREHOUSE.code()),
          new Preset(
              "WH_MGR",
              "Warehouse Manager",
              "Manages warehouse operations and inventory",
              "MANAGER",
              SystemDepartment.WAREHOUSE.code()),
          new Preset(
              "YARN_WH",
              "Yarn Warehouse Clerk",
              "Handles yarn receiving and dispatch",
              "WORKER",
              SystemDepartment.WAREHOUSE.code()),
          new Preset(
              "FAB_WH",
              "Fabric Warehouse Clerk",
              "Handles greige and finished fabric inventory",
              "WORKER",
              SystemDepartment.WAREHOUSE.code()),
          new Preset(
              "CHEM_WH",
              "Chemical Warehouse Clerk",
              "Handles dyes and hazardous chemicals",
              "WORKER",
              SystemDepartment.WAREHOUSE.code()),
          new Preset(
              "LOG_COORD",
              "Logistics Coordinator",
              "Coordinates customs, freight, and export",
              "WORKER",
              SystemDepartment.SHIPPING.code()),
          new Preset(
              "DISPATCHER",
              "Dispatcher",
              "Coordinates truck loading and packing lists",
              "WORKER",
              SystemDepartment.SHIPPING.code()),

          // ── 7. Procurement & Sourcing ──
          new Preset(
              "PROC_MGR",
              "Procurement Manager",
              "Manages strategic sourcing and purchasing",
              "MANAGER",
              SystemDepartment.PROCUREMENT.code()),
          new Preset(
              "YARN_BUYER",
              "Yarn & Fiber Buyer",
              "Purchases raw products",
              "WORKER",
              SystemDepartment.PROCUREMENT.code()),
          new Preset(
              "CHEM_BUYER",
              "Chemical Buyer",
              "Purchases dyes and auxiliary chemicals",
              "WORKER",
              SystemDepartment.PROCUREMENT.code()),
          new Preset(
              "SOURCER",
              "Sourcing Specialist",
              "Manages outsourced production (subcontractors)",
              "WORKER",
              SystemDepartment.PROCUREMENT.code()),

          // ── 8. Sales & Merchandising ──
          new Preset(
              "SALES_DIR",
              "Sales Director",
              "Leads global sales strategy",
              "MANAGER",
              SystemDepartment.SALES.code()),
          new Preset(
              "SALES_REP",
              "Sales Representative",
              "Handles customer orders and quotations",
              "WORKER",
              SystemDepartment.SALES.code()),
          new Preset(
              "SR_MERCH",
              "Senior Merchandiser",
              "Manages key accounts and order lifecycle",
              "SUPERVISOR",
              SystemDepartment.SALES.code()),
          new Preset(
              "MERCH",
              "Merchandiser",
              "Tracks production and sampling orders",
              "WORKER",
              SystemDepartment.SALES.code()),
          new Preset(
              "SHOWROOM",
              "Showroom Coordinator",
              "Manages physical sample display and swatches",
              "WORKER",
              SystemDepartment.SALES.code()),

          // ── 9. Design, R&D & Planning ──
          new Preset(
              "PLAN_MGR",
              "Planning Manager",
              "Manages capacity and lead times",
              "MANAGER",
              SystemDepartment.PLANNING.code()),
          new Preset(
              "PLANNER",
              "Production Planner",
              "Schedules machines and work orders",
              "WORKER",
              SystemDepartment.PLANNING.code()),
          new Preset(
              "RD_MGR",
              "R&D Manager",
              "Manages new product development",
              "MANAGER",
              SystemDepartment.RD.code()),
          new Preset(
              "FAB_DESIGN",
              "Fabric Designer",
              "Designs Dobby/Jacquard patterns (CAD)",
              "WORKER",
              SystemDepartment.RD.code()),
          new Preset(
              "TECHNOLOG",
              "Fabric Technologist",
              "Defines fabric specifications and routes",
              "WORKER",
              SystemDepartment.RD.code()),
          new Preset(
              "SAMPLE_MKR",
              "Sample Maker",
              "Produces physical samples for clients",
              "WORKER",
              SystemDepartment.RD.code()));

  private JobTitleSeedData() {}
}
