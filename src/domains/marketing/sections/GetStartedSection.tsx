// src/domains/marketing/sections/GetStartedSection.tsx

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
            background: `linear-gradient(135deg, rgba(30, 58, 138, 0.95), rgba(15, 23, 42, 0.92))`,
            padding: "clamp(2rem, 6vw, 3.75rem)",
            position: "relative",
            overflow: "hidden",
            color: colorTokens.text.inverse,
          }}
        >
          <div
            aria-hidden
            style={{
              position: "absolute",
              inset: 0,
              background:
                "radial-gradient(circle at top left, rgba(255,255,255,0.12) 0%, transparent 55%), radial-gradient(circle at bottom right, rgba(59,130,246,0.22) 0%, transparent 50%)",
            }}
          />

          <div style={{ position: "relative", zIndex: 1 }} className="space-y-6">
            <span
              style={{
                textTransform: "uppercase",
                letterSpacing: "0.2em",
                fontSize: "0.75rem",
                color: "rgba(248, 250, 255, 0.7)",
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
                color: "rgba(248, 250, 255, 0.78)",
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
                background: colorTokens.background.surface,
                color: colorTokens.accent.primary,
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
