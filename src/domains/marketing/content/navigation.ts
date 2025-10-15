// src/domains/marketing/content/navigation.ts

export type MarketingNavLink = {
  label: string;
  href: string;
};

export const marketingNavLinks: MarketingNavLink[] = [
  { label: "Modules", href: "#modules" },
  { label: "Pricing", href: "#pricing" },
  { label: "About", href: "#about" },
  { label: "Support", href: "#support" },
];
