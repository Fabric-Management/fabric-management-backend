// fabric-management-frontend/src/domains/marketing/content/modules.ts

export type FabricodeModule = {
  id: string;
  name: string;
  headline: string;
  icon: string;
  href: string;
};

export const fabricodeModules: FabricodeModule[] = [
  {
    id: "weaveOS",
    name: "waeveOS",
    headline: "Monitors weaving machines and fabric quality.",
    icon: "🧵",
    href: "/modules/weave-os",
  },
  {
    id: "dyeOS",
    name: "dyeOS",
    headline: "Manages dyeing and finishing processes.",
    icon: "🎨",
    href: "/modules/dye-os",
  },
  {
    id: "flowOS",
    name: "flowOS",
    headline: "Tracks business workflows and process automation.",
    icon: "⚙️",
    href: "/modules/flow-os",
  },
  {
    id: "todOS",
    name: "todOS",
    headline: "Productivity system integrated with order workflows.",
    icon: "✅",
    href: "/modules/todos",
  },
  {
    id: "stockOS",
    name: "stockOS",
    headline: "Smart inventory and warehouse tracking.",
    icon: "📦",
    href: "/modules/stock-os",
  },
  {
    id: "logisticsOS",
    name: "logisticsOS",
    headline: "Manages shipments and delivery chains.",
    icon: "🚚",
    href: "/modules/logistics-os",
  },
  {
    id: "trackOS",
    name: "trackOS",
    headline: "Field staff and vehicle tracking.",
    icon: "📍",
    href: "/modules/track-os",
  },
  {
    id: "orderOS",
    name: "orderOS",
    headline: "Order management and client tracking.",
    icon: "🧾",
    href: "/modules/order-os",
  },
  {
    id: "aiOS",
    name: "AIOS",
    headline: "AI assistant for real-time operational insights.",
    icon: "✨",
    href: "/modules/ai-os",
  },
];
