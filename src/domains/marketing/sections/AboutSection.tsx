// src/domains/marketing/sections/AboutSection.tsx

import { FullWidthSection } from "@/ui/components/sections/FullWidthSection";
import { Container } from "@/ui/components/layout/Container";
import { colorTokens, typographyTokens, layoutTokens } from "@/ui/theme/tokens";

export function AboutSection() {
  return (
    <FullWidthSection id="about" subdued>
      <Container className="grid gap-12 lg:grid-cols-[minmax(0,1fr)_minmax(0,1fr)]">
        <div
          className="relative flex items-center justify-center"
          style={{
            background: "linear-gradient(135deg, rgba(30,58,138,0.1), rgba(15,23,42,0.08))",
            borderRadius: layoutTokens.radius.lg,
            padding: "2.5rem",
            minHeight: "320px",
          }}
        >
          <div
            style={{
              position: "absolute",
              inset: "18%",
              borderRadius: layoutTokens.radius.md,
              border: `1px solid ${colorTokens.border.strong}`,
              transform: "rotate(-5deg)",
            }}
            aria-hidden
          />
          <div
            style={{
              position: "absolute",
              inset: "12%",
              borderRadius: layoutTokens.radius.md,
              border: `1px solid ${colorTokens.accent.primary}`,
              transform: "rotate(4deg)",
            }}
            aria-hidden
          />
          <div
            style={{
              position: "relative",
              zIndex: 1,
              display: "grid",
              gap: "14px",
              textAlign: "center",
              color: colorTokens.text.primary,
            }}
          >
            <div
              style={{
                display: "grid",
                gridTemplateColumns: "repeat(3, minmax(0, 80px))",
                gap: "14px",
              }}
            >
              {["weaveOS", "dyeOS", "flowOS", "stockOS", "orderOS", "AIOS"].map((module) => (
                <div
                  key={module}
                  style={{
                    borderRadius: layoutTokens.radius.md,
                    padding: "14px",
                    background: colorTokens.background.surface,
                    border: `1px solid ${colorTokens.border.default}`,
                    fontSize: "0.8rem",
                    fontWeight: typographyTokens.weight.medium,
                    boxShadow: "0 8px 20px rgba(15, 23, 42, 0.08)",
                  }}
                >
                  {module}
                </div>
              ))}
            </div>
            <span
              style={{
                fontSize: "0.75rem",
                textTransform: "uppercase",
                letterSpacing: "0.2em",
                color: colorTokens.text.secondary,
              }}
            >
              Fabricode OS Core
            </span>
          </div>
        </div>

        <div className="space-y-6">
          <h2
            style={{
              fontSize: typographyTokens.sizes.sectionTitle,
              fontWeight: typographyTokens.weight.semibold,
              color: colorTokens.text.primary,
            }}
          >
            Modular platform crafted for every textile tenant
          </h2>
          <p
            style={{
              color: colorTokens.text.secondary,
              fontSize: typographyTokens.sizes.body,
              lineHeight: 1.75,
            }}
          >
            Fabricode is a modular, multitenant platform built to simplify textile production management — from cotton to finished fabric. Each OS module handles a specific process, integrated seamlessly under one ecosystem and secured by tenant-aware access controls.
          </p>
          <p
            style={{
              color: colorTokens.text.secondary,
              fontSize: typographyTokens.sizes.body,
              lineHeight: 1.75,
            }}
          >
            Deploy tailored workflows for weaving, dyeing, logistics, and quality while sharing insights across all your mills. Fabricode scales with every site, ensuring your teams operate on a unified yet personalized operating system.
          </p>
        </div>
      </Container>
    </FullWidthSection>
  );
}
