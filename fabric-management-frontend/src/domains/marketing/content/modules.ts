// fabric-management-frontend/src/domains/marketing/content/modules.ts

export type FabricodeModule = {
  id: string;
  name: string;
  icon: string;
  title: string;
  headline: string;
  description: string;
  capabilities: string[];
};

export const fabricodeModules: FabricodeModule[] = [
  {
    id: "fabricOS",
    name: "FabricOS",
    icon: "🧠",
    title: "The Core of Intelligent Textile Management",
    headline: "Where every thread of your business connects.",
    description:
      "FabricOS is the central operating system that powers the entire Fabricode ecosystem, orchestrating tenant management, data unification, and cross-module intelligence. It delivers a single source of truth for production, commercial, and operational data, ensuring every decision is informed by live manufacturing context.",
    capabilities: [
      "Unified tenant directory with granular access controls",
      "Data fabric that normalises metrics across every module",
      "Process orchestration hub with configurable automations",
      "Real-time observability dashboards spanning mills and partners",
      "Secure API layer for ERP, MES, and finance integrations",
    ],
  },
  {
    id: "fiberOS",
    name: "fiberOS",
    icon: "🧵",
    title: "Raw Material Intelligence",
    headline: "Control begins where the fiber begins.",
    description:
      "fiberOS digitises raw fibre sourcing, quality, and traceability so procurement and sustainability teams see the full lifecycle of every batch. It harmonises supplier data, testing certificates, and material costs to support strategic purchasing and compliance at scale.",
    capabilities: [
      "Supplier scorecards with quality trend analytics",
      "Lot-level traceability from farm to mill inbound docks",
      "Automated compliance documentation and certification tracking",
      "Cost variance monitoring with predictive restock signals",
      "Sustainability insights aligned with ESG reporting frameworks",
    ],
  },
  {
    id: "yarnOS",
    name: "yarnOS",
    icon: "🪡",
    title: "Yarn Production & Performance",
    headline: "From fiber to yarn — precision at every twist.",
    description:
      "yarnOS synchronises spinning operations, monitoring production lines, spindle efficiency, and quality checks in one command centre. It turns machine telemetry and lab results into actionable insights that reduce waste, downtime, and energy spend.",
    capabilities: [
      "Live spindle, winding, and draft monitoring across lines",
      "Quality variance alerts tied to fibre batch provenance",
      "Maintenance intelligence with recommended interventions",
      "Energy consumption benchmarking per yarn specification",
      "Scenario planning for blend ratios and throughput targets",
    ],
  },
  {
    id: "waeveOS",
    name: "waeveOS",
    icon: "🧶",
    title: "Weaving Process Intelligence",
    headline: "Smart weaving, smarter production.",
    description:
      "waeveOS gives weaving supervisors precise control of looms, fabric designs, and efficiency metrics. It connects recipe management, stoppage analysis, and quality inspection data so you can balance speed with craftsmanship every shift.",
    capabilities: [
      "Configurable loom profiles with digital recipe libraries",
      "Predictive stoppage insights using historical downtime patterns",
      "Fabric defect classification with root-cause heatmaps",
      "Changeover planning with material and workforce readiness cues",
      "Line performance benchmarking across tenant sites",
    ],
  },
  {
    id: "dyeOS",
    name: "dyeOS",
    icon: "🎨",
    title: "Dyeing & Finishing Excellence",
    headline: "Perfect color, flawless finish.",
    description:
      "dyeOS unifies color science, chemical inventory, and finishing schedules to deliver consistent output every run. From lab dip approvals to bulk execution, it keeps formulation, energy usage, and environmental impact under precise control.",
    capabilities: [
      "Digital shade library with approved formulation histories",
      "Automated machine set-up sheets with chemical dosing plans",
      "Real-time bath monitoring and corrective action workflows",
      "Effluent and energy footprint analytics for sustainability KPIs",
      "Customer-facing quality certificates generated on approval",
    ],
  },
  {
    id: "flowOS",
    name: "flowOS",
    icon: "🔄",
    title: "Workflow Automation & Process Management",
    headline: "Your business, flowing intelligently.",
    description:
      "flowOS choreographs every cross-functional process, from sampling to shipping, through configurable workflows. It maps dependencies, approvals, and SLAs so teams can collaborate in context with clear accountability.",
    capabilities: [
      "Visual workflow builder with reusable textile templates",
      "Conditional automation driven by production events",
      "Role-based task routing with SLA monitoring",
      "Workflow analytics highlighting bottlenecks and variance",
      "Native integrations with email, chat, and ticketing systems",
    ],
  },
  {
    id: "todOS",
    name: "todOS",
    icon: "✅",
    title: "Task & Productivity Management",
    headline: "From tasks to teamwork — clarity in motion.",
    description:
      "todOS aligns every team member with daily priorities, linking tasks to orders, machines, and compliance steps. Supervisors gain clarity, operators get concise instructions, and leadership sees progress in real time.",
    capabilities: [
      "Task boards linked to production orders and workflows",
      "Shift handover summaries with outstanding critical actions",
      "Mobile-first operator checklists with photo evidence capture",
      "Performance scorecards for teams and individual roles",
      "Automated nudges for escalations and blocked activities",
    ],
  },
  {
    id: "stockOS",
    name: "stockOS",
    icon: "📦",
    title: "Smart Inventory & Warehouse Tracking",
    headline: "Know exactly what you have, where it is, and what it’s worth.",
    description:
      "stockOS provides end-to-end visibility of yarn, fabric, trims, and finished goods across every warehouse and staging zone. It blends RFID, barcode, and manual inputs to create an accurate, real-time inventory backbone.",
    capabilities: [
      "Multi-location inventory ledger with valuation snapshots",
      "Put-away and pick-path optimisation for high-velocity items",
      "Cycle counting automation with variance reconciliation",
      "Lot expiry and hold status alerts tied to quality events",
      "Integrations with WMS, ERP, and logistics providers",
    ],
  },
  {
    id: "logisticsOS",
    name: "logisticsOS",
    icon: "🚚",
    title: "Shipment & Delivery Control",
    headline: "From factory to customer — perfectly on track.",
    description:
      "logisticsOS centralises dispatch planning, carrier collaboration, and delivery monitoring so every order leaves and arrives on time. It eliminates spreadsheet chaos with live milestones shared across customers and internal teams.",
    capabilities: [
      "Shipment scheduling with production-readiness validation",
      "Carrier portal for document exchange and status updates",
      "Route optimisation with live traffic and cost considerations",
      "Exception management workflows for delays and damages",
      "Customer-facing tracking pages branded for each tenant",
    ],
  },
  {
    id: "trackOS",
    name: "trackOS",
    icon: "📍",
    title: "Field Staff & Vehicle Tracking",
    headline: "Visibility beyond factory walls.",
    description:
      "trackOS brings fleet and field operatives into the same command centre as your plants. Supervisors see GPS, task assignments, and compliance logs in real time to coordinate after-sales service, sourcing trips, and delivery confirmations.",
    capabilities: [
      "Real-time GPS for fleet, inspectors, and merchandisers",
      "Geo-fenced alerts for critical checkpoints and restricted zones",
      "Digital proof of delivery with signatures and photo capture",
      "Driver performance analytics with safety scoring",
      "Offline-ready mobile app syncing when connectivity returns",
    ],
  },
  {
    id: "orderOS",
    name: "orderOS",
    icon: "🛒",
    title: "Smart Order Management",
    headline: "Sales intelligence meets production awareness.",
    description:
      "orderOS unites sales, planning, and production so every order is executed against live capacity and material realities. It automates confirmations, change requests, and margin analysis to keep customers informed and revenue protected.",
    capabilities: [
      "Unified order intake across channels with validation rules",
      "Commit date calculations using live capacity snapshots",
      "Margin and profitability analytics down to SKU level",
      "Change control workflows connected to production planners",
      "Integrated customer communication timeline and audit trail",
    ],
  },
  {
    id: "aiOS",
    name: "AIOS",
    icon: "🤖",
    title: "Conversational Intelligence Assistant",
    headline: "Talk to your factory.",
    description:
      "AIOS surfaces conversational insights from every Fabricode module, letting leaders ask questions and trigger actions in natural language. It combines operational data, predictive models, and copilots tailored for textile roles.",
    capabilities: [
      "Natural language dashboards across production and supply chain",
      "Voice and chat copilots embedded in every module workflow",
      "Predictive recommendations for throughput, quality, and cost",
      "Automated report generation with contextual commentary",
      "Secure guardrails tuned to each tenant’s governance policies",
    ],
  },
  {
    id: "designOS",
    name: "designOS",
    icon: "🧩",
    title: "Intelligent Textile Design Platform",
    headline: "From imagination to fabric — powered by AI.",
    description:
      "designOS empowers creative teams to prototype, simulate, and approve textile designs with manufacturing feasibility in mind. It connects designers, merchandisers, and mills through shared palettes, digital twins, and instant costing.",
    capabilities: [
      "AI-assisted pattern generation aligned with brand DNA",
      "Digital twin simulations for drape, weight, and durability",
      "Palette management linked to approved dye formulations",
      "Collaborative design reviews with inline manufacturing feedback",
      "Automated BOM and costing packs ready for production hand-off",
    ],
  },
];
