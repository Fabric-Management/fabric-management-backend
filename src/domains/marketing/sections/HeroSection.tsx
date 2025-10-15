// src/domains/marketing/sections/HeroSection.tsx

import Link from "next/link";
import Image from "next/image";
import { FullWidthSection } from "@/ui/components/sections/FullWidthSection";
import { Container } from "@/ui/components/layout/Container";
import { colorTokens, typographyTokens, layoutTokens } from "@/ui/theme/tokens";

export function HeroSection() {
  return (
    <FullWidthSection>
      <Container className="grid gap-16 lg:grid-cols-[minmax(0,1fr)_minmax(0,1.1fr)] items-center">
        <div className="space-y-8" data-animate="fade-up">
          <span
            style={{
              display: "inline-flex",
              alignItems: "center",
              gap: "12px",
              fontSize: typographyTokens.sizes.overline,
              fontWeight: typographyTokens.weight.medium,
              textTransform: "uppercase",
              letterSpacing: "0.18em",
              color: colorTokens.accent.primary,
              background: colorTokens.accent.subtle,
              padding: "0.6rem 1.1rem",
              borderRadius: layoutTokens.radius.md,
            }}
          >
            Multitenant · Intelligent · Modular
          </span>

          <h1
            style={{
              fontSize: typographyTokens.sizes.hero,
              fontWeight: typographyTokens.weight.semibold,
              lineHeight: 1.05,
              color: colorTokens.text.primary,
            }}
          >
            The Intelligent Operating System for Textile Manufacturing
          </h1>

          <p
            style={{
              fontSize: typographyTokens.sizes.subtitle,
              color: colorTokens.text.secondary,
              maxWidth: "32rem",
              lineHeight: 1.6,
            }}
          >
            Unify your weaving, dyeing, logistics, and workflow operations under one smart ecosystem built for every tenant in your organization.
          </p>

          <div className="flex flex-col sm:flex-row gap-4">
            <Link
              href="/register"
              style={{
                padding: "1rem 1.75rem",
                borderRadius: "999px",
                background: `linear-gradient(135deg, ${colorTokens.accent.gradientFrom}, ${colorTokens.accent.gradientTo})`,
                color: colorTokens.text.inverse,
                fontWeight: typographyTokens.weight.medium,
                textAlign: "center",
                boxShadow: colorTokens.shadow.soft,
              }}
            >
              Get Fabricode Now
            </Link>
            <Link
              href="#modules"
              style={{
                padding: "1rem 1.75rem",
                borderRadius: "999px",
                border: `1px solid ${colorTokens.border.default}`,
                color: colorTokens.text.primary,
                fontWeight: typographyTokens.weight.medium,
                textAlign: "center",
              }}
            >
              Explore Modules
            </Link>
          </div>
        </div>

        <div className="relative flex items-center justify-center" data-animate="fade-up">
          <div
            style={{
              position: "relative",
              width: "100%",
              borderRadius: layoutTokens.radius.xl,
              padding: "2.75rem",
              background: "linear-gradient(160deg, rgba(30, 58, 138, 0.1), rgba(15, 23, 42, 0.05))",
              boxShadow: colorTokens.shadow.soft,
              overflow: "hidden",
            }}
          >
            <div
              aria-hidden
              style={{
                position: "absolute",
                inset: "12%",
                borderRadius: "24px",
                border: `1px solid rgba(30, 58, 138, 0.35)`,
                transform: "rotate(3deg)",
              }}
            />

            <Image
              src="/images/fabricode-hero-grid.png"
              alt="Fabricode OS modules connected"
              width={640}
              height={460}
              style={{
                width: "100%",
                height: "auto",
                borderRadius: layoutTokens.radius.lg,
              }}
              priority
            />
          </div>
        </div>
      </Container>
    </FullWidthSection>
  );
}
