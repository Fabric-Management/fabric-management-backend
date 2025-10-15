// fabric-management-frontend/src/domains/marketing/sections/SupportSection.tsx

import Link from "next/link";
import { FullWidthSection } from "@/ui/components/sections/FullWidthSection";
import { Container } from "@/ui/components/layout/Container";
import { colorTokens, layoutTokens, typographyTokens } from "@/ui/theme/tokens";

const supportCards = [
  {
    title: "Implementation Desk",
    description: "Dedicated textile specialists to guide tenant rollout, integrations, and module calibration.",
    cta: "Book onboarding call",
    href: "mailto:implement@fabricode.io",
  },
  {
    title: "Support Center",
    description: "Knowledge base and ticketing tailored for weaving, dyeing, logistics, and QA teams.",
    cta: "Visit support portal",
    href: "https://support.fabricode.io",
  },
  {
    title: "Developer Hub",
    description: "API reference, SDKs, and sandbox tenants to extend Fabricode OS into your infrastructure.",
    cta: "Explore developer docs",
    href: "https://developers.fabricode.io",
  },
];

export function SupportSection() {
  return (
    <FullWidthSection id="support">
      <Container className="space-y-12">
        <div className="max-w-2xl space-y-4">
          <h2
            style={{
              fontSize: typographyTokens.sizes.sectionTitle,
              fontWeight: typographyTokens.weight.semibold,
            }}
          >
            Enterprise-grade support from day zero
          </h2>
          <p
            style={{
              color: colorTokens.text.secondary,
              lineHeight: 1.7,
            }}
          >
            From implementation to everyday operations, Fabricode partners with your mills and teams to keep production flowing.
          </p>
        </div>

        <div
          className="grid gap-6"
          style={{
            gridTemplateColumns: "repeat(auto-fit, minmax(240px, 1fr))",
          }}
        >
          {supportCards.map((card) => (
            <div
              key={card.title}
              style={{
                padding: "26px",
                borderRadius: layoutTokens.radius.lg,
                border: `1px solid ${colorTokens.border.default}`,
                background: colorTokens.background.surface,
                display: "flex",
                flexDirection: "column",
                gap: "18px",
                boxShadow: "0 16px 34px rgba(15, 23, 42, 0.05)",
              }}
            >
              <div>
                <h3
                  style={{
                    fontSize: "1.2rem",
                    fontWeight: typographyTokens.weight.semibold,
                    color: colorTokens.text.primary,
                  }}
                >
                  {card.title}
                </h3>
                <p
                  style={{
                    marginTop: "10px",
                    color: colorTokens.text.secondary,
                    lineHeight: 1.6,
                  }}
                >
                  {card.description}
                </p>
              </div>
              <Link
                href={card.href}
                style={{
                  marginTop: "auto",
                  color: colorTokens.accent.primary,
                  fontWeight: typographyTokens.weight.medium,
                }}
              >
                {card.cta} →
              </Link>
            </div>
          ))}
        </div>
      </Container>
    </FullWidthSection>
  );
}
