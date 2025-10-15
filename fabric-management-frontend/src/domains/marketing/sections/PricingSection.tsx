// fabric-management-frontend/src/domains/marketing/sections/PricingSection.tsx

import Link from "next/link";
import { FullWidthSection } from "@/ui/components/sections/FullWidthSection";
import { Container } from "@/ui/components/layout/Container";
import { colorTokens, layoutTokens, typographyTokens } from "@/ui/theme/tokens";

const inclusions = [
  "Unlimited tenants with granular access controls",
  "Real-time telemetry across weaving, dyeing, and logistics",
  "AI insights with anomaly detection",
  "API access to integrate with ERP and MES stacks",
  "Priority support with dedicated textile specialists",
];

export function PricingSection() {
  return (
    <FullWidthSection id="pricing" subdued>
      <Container className="space-y-12">
        <div className="text-center space-y-6 max-w-2xl mx-auto">
          <p
            style={{
              textTransform: "uppercase",
              letterSpacing: "0.18em",
              fontSize: "0.75rem",
              color: colorTokens.text.secondary,
            }}
          >
            Pricing
          </p>
          <h2
            style={{
              fontSize: typographyTokens.sizes.sectionTitle,
              fontWeight: typographyTokens.weight.semibold,
            }}
          >
            Start free, scale with your production network
          </h2>
          <p
            style={{
              color: colorTokens.text.secondary,
              lineHeight: 1.7,
            }}
          >
            Fabricode OS launches with a 6-month free pilot. Move to a transparent enterprise plan once your modules are live across tenants.
          </p>
        </div>

        <div
          style={{
            margin: "0 auto",
            maxWidth: "560px",
            padding: "40px",
            borderRadius: layoutTokens.radius.xl,
            background: colorTokens.accent.subtle,
          }}
        >
          <div className="flex flex-col gap-6">
            <div>
              <h3
                style={{
                  fontSize: "1.4rem",
                  fontWeight: typographyTokens.weight.semibold,
                }}
              >
                Fabricode Core Platform
              </h3>
              <p
                style={{
                  marginTop: "8px",
                  color: colorTokens.text.secondary,
                }}
              >
                Includes base OS, tenancy controls, analytics, and automation engine.
              </p>
            </div>

            <div>
              <span
                style={{
                  display: "block",
                  fontSize: "0.85rem",
                  textTransform: "uppercase",
                  letterSpacing: "0.18em",
                  color: colorTokens.accent.primary,
                }}
              >
                Pilot
              </span>
              <div
                style={{
                  display: "flex",
                  alignItems: "baseline",
                  gap: "8px",
                  marginTop: "12px",
                }}
              >
                <span
                  style={{
                    fontSize: "2.5rem",
                    fontWeight: typographyTokens.weight.semibold,
                  }}
                >
                  Free
                </span>
                <span style={{ color: colorTokens.text.secondary }}>for 6 months</span>
              </div>
              <p style={{ color: colorTokens.text.secondary, marginTop: "4px" }}>
                Then $299 / month per active module
              </p>
            </div>

            <ul className="space-y-3" style={{ marginTop: "12px" }}>
              {inclusions.map((item) => (
                <li key={item} style={{ display: "flex", gap: "12px", alignItems: "flex-start" }}>
                  <span aria-hidden style={{ color: colorTokens.accent.primary, marginTop: "4px" }}>
                    •
                  </span>
                  <span style={{ color: colorTokens.text.secondary, lineHeight: 1.6 }}>{item}</span>
                </li>
              ))}
            </ul>

            <Link
              href="/register"
              style={{
                marginTop: "8px",
                display: "inline-flex",
                justifyContent: "center",
                padding: "0.95rem",
                borderRadius: layoutTokens.radius.md,
                background: colorTokens.accent.primary,
                color: colorTokens.text.inverse,
                fontWeight: typographyTokens.weight.medium,
                letterSpacing: "0.01em",
              }}
            >
              Start 6-Month Pilot
            </Link>
          </div>
        </div>
      </Container>
    </FullWidthSection>
  );
}
