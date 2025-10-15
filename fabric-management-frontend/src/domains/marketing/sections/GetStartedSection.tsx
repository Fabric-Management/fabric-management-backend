// fabric-management-frontend/src/domains/marketing/sections/GetStartedSection.tsx

import Link from "next/link";
import { FullWidthSection } from "@/ui/components/sections/FullWidthSection";
import { Container } from "@/ui/components/layout/Container";
import { colorTokens, typographyTokens, layoutTokens } from "@/ui/theme/tokens";

export function GetStartedSection() {
  return (
    <FullWidthSection id="cta">
      <Container>
        <div
          style={{
            borderRadius: layoutTokens.radius.xl,
            background: colorTokens.accent.subtle,
            padding: "clamp(2rem, 6vw, 3.75rem)",
            color: colorTokens.text.primary,
            display: "grid",
            gap: "1.5rem",
          }}
        >
          <div className="space-y-6">
            <span
              style={{
                textTransform: "uppercase",
                letterSpacing: "0.2em",
                fontSize: "0.75rem",
                color: colorTokens.accent.primary,
                fontWeight: typographyTokens.weight.medium,
              }}
            >
              Get Started
            </span>
            <h2
              style={{
                fontSize: typographyTokens.sizes.sectionTitle,
                fontWeight: typographyTokens.weight.semibold,
                lineHeight: 1.1,
              }}
            >
              Start your journey with Fabricode today.
            </h2>
            <p
              style={{
                color: colorTokens.text.tertiary,
                lineHeight: 1.7,
              }}
            >
              Get 6 months free — build your first module and experience the future of textile management. Ready when you decide to scale.
            </p>
            <Link
              href="/register"
              style={{
                display: "inline-flex",
                padding: "1rem 1.75rem",
                borderRadius: "999px",
                background: colorTokens.accent.primary,
                color: colorTokens.text.inverse,
                fontWeight: typographyTokens.weight.medium,
                letterSpacing: "0.01em",
              }}
            >
              Get Fabricode Now
            </Link>
          </div>
        </div>
      </Container>
    </FullWidthSection>
  );
}
