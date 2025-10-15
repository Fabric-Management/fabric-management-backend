// fabric-management-frontend/src/domains/marketing/sections/ModulesSection.tsx

import Link from "next/link";
import { FullWidthSection } from "@/ui/components/sections/FullWidthSection";
import { Container } from "@/ui/components/layout/Container";
import { colorTokens, typographyTokens, layoutTokens } from "@/ui/theme/tokens";
import { fabricodeModules } from "@/domains/marketing/content/modules";

export function ModulesSection() {
  return (
    <FullWidthSection id="modules">
      <Container className="space-y-12">
        <div className="grid gap-6 lg:grid-cols-[minmax(0,2fr)_minmax(0,1fr)] lg:items-end">
          <div>
            <p
              style={{
                textTransform: "uppercase",
                letterSpacing: "0.18em",
                fontSize: "0.75rem",
                color: colorTokens.text.secondary,
                marginBottom: "12px",
              }}
            >
              OS Modules
            </p>
            <h2
              style={{
                fontSize: typographyTokens.sizes.sectionTitle,
                fontWeight: typographyTokens.weight.semibold,
                color: colorTokens.text.primary,
                lineHeight: 1.1,
              }}
            >
              Explore Fabricode Modules
            </h2>
          </div>
          <p
            style={{
              color: colorTokens.text.secondary,
              lineHeight: 1.7,
            }}
          >
            Each module focuses on a distinct textile process. Combine them like building blocks to create the operating system your mill deserves. Launch a module in minutes, provision it to specific tenants, and monitor performance across the entire network.
          </p>
        </div>

        <div
          className="grid gap-6"
          style={{
            gridTemplateColumns: "repeat(auto-fit, minmax(220px, 1fr))",
          }}
        >
          {fabricodeModules.map((module) => (
            <Link
              key={module.id}
              href={module.href}
              className="group"
              style={{
                display: "flex",
                flexDirection: "column",
                gap: "18px",
                padding: "24px",
                borderRadius: layoutTokens.radius.lg,
                background: colorTokens.background.surface,
                border: `1px solid ${colorTokens.border.default}`,
                boxShadow: "0 16px 34px rgba(15, 23, 42, 0.06)",
                transition: "transform 0.25s ease, box-shadow 0.25s ease",
              }}
            >
              <span
                aria-hidden
                style={{
                  fontSize: "2rem",
                }}
              >
                {module.icon}
              </span>
              <div>
                <p
                  style={{
                    fontWeight: typographyTokens.weight.semibold,
                    fontSize: "1.1rem",
                    color: colorTokens.text.primary,
                  }}
                >
                  {module.name}
                </p>
                <p
                  style={{
                    marginTop: "8px",
                    color: colorTokens.text.secondary,
                    lineHeight: 1.5,
                    fontSize: "0.95rem",
                  }}
                >
                  {module.headline}
                </p>
              </div>
              <span
                style={{
                  marginTop: "auto",
                  fontSize: "0.95rem",
                  color: colorTokens.accent.primary,
                  fontWeight: typographyTokens.weight.medium,
                }}
              >
                Learn More →
              </span>
            </Link>
          ))}
        </div>
      </Container>
    </FullWidthSection>
  );
}
