// src/app/(marketing)/landing/page.tsx

import { HeroSection } from "@/domains/marketing/sections/HeroSection";
import { AboutSection } from "@/domains/marketing/sections/AboutSection";
import { ModulesSection } from "@/domains/marketing/sections/ModulesSection";
import { PricingSection } from "@/domains/marketing/sections/PricingSection";
import { SupportSection } from "@/domains/marketing/sections/SupportSection";
import { GetStartedSection } from "@/domains/marketing/sections/GetStartedSection";

export default function LandingPage() {
  return (
    <>
      <HeroSection />
      <AboutSection />
      <ModulesSection />
      <PricingSection />
      <SupportSection />
      <GetStartedSection />
    </>
  );
}
