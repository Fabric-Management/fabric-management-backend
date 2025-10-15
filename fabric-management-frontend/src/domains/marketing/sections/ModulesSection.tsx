// fabric-management-frontend/src/domains/marketing/sections/ModulesSection.tsx

import { FullWidthSection } from "@/ui/components/sections/FullWidthSection";
import { Container } from "@/ui/components/layout/Container";
import { colorTokens, typographyTokens, layoutTokens } from "@/ui/theme/tokens";
import { fabricodeModules } from "@/domains/marketing/content/modules";

export function ModulesSection() {
  const pastelPalette = [
    "#EEF3FF",
    "#F9FAFB",
    "#E9F6FF",
    "#F4F5FF",
    "#F7FBFF",
  ];

  return (
    <FullWidthSection id="modules">
      <Container className="space-y-16">
        <div className="grid gap-6 lg:grid-cols-[minmax(0,2fr)_minmax(0,1fr)] lg:items-end">
          <div>
            <p
              style={{
                textTransform: "uppercase",
                letterSpacing: "0.18em",
                fontSize: "0.75rem",
                color: colorTokens.text.tertiary,
                marginBottom: "12px",
              }}
            >
              OS Modules
            </p>
            <h2
              style={{
                fontSize: typographyTokens.sizes.sectionTitle,
                fontWeight: typographyTokens.weight.semibold,
                lineHeight: 1.1,
              }}
            >
              Explore Fabricode Modules
            </h2>
          </div>
          <p
            style={{
              color: colorTokens.text.tertiary,
              lineHeight: 1.7,
            }}
          >
            Each module focuses on a distinct textile process. Combine them like building blocks to create the operating system your mill deserves. Launch a module in minutes, provision it to specific tenants, and monitor performance across the entire network.
          </p>
        </div>

        <div
          className="grid gap-10"
          style={{
            gridTemplateColumns: "repeat(auto-fit, minmax(280px, 1fr))",
          }}
        >
          {fabricodeModules.map((module, index) => (
            <article
              key={module.id}
              style={{
                background:
                  pastelPalette[index % pastelPalette.length],
                display: "flex",
                flexDirection: "column",
                gap: "1.5rem",
                padding: "2.25rem",
                borderRadius: layoutTokens.radius.lg,
              }}
            >
              <header style={{ display: "flex", flexDirection: "column", gap: "0.75rem" }}>
                <div style={{ fontSize: "2rem" }} aria-hidden>
                  {module.icon}
                </div>
                <div style={{ display: "flex", flexDirection: "column", gap: "0.25rem" }}>
                  <p
                    style={{
                      fontSize: "0.9rem",
                      textTransform: "uppercase",
                      letterSpacing: "0.2em",
                      fontWeight: typographyTokens.weight.medium,
                      color: colorTokens.text.tertiary,
                    }}
                  >
                    {module.name}
                  </p>
                  <h3
                    style={{
                      fontSize: "1.3rem",
                      fontWeight: typographyTokens.weight.semibold,
                      lineHeight: 1.35,
                    }}
                  >
                    {module.title}
                  </h3>
                  <p
                    style={{
                      fontSize: "1rem",
                      color: colorTokens.accent.primary,
                      fontWeight: typographyTokens.weight.medium,
                    }}
                  >
                    “{module.headline}”
                  </p>
                </div>
              </header>
              <p
                style={{
                  color: colorTokens.text.secondary,
                  lineHeight: 1.65,
                }}
              >
                {module.description}
              </p>

              <section style={{ display: "flex", flexDirection: "column", gap: "0.65rem" }}>
                <p
                  style={{
                    fontSize: "0.85rem",
                    textTransform: "uppercase",
                    letterSpacing: "0.18em",
                    color: colorTokens.text.tertiary,
                    fontWeight: typographyTokens.weight.medium,
                  }}
                >
                  Key capabilities
                </p>
                <ul style={{ display: "flex", flexDirection: "column", gap: "0.5rem", paddingLeft: "1.25rem", margin: 0 }}>
                  {module.capabilities.map((item) => (
                    <li
                      key={item}
                      style={{
                        color: colorTokens.text.secondary,
                        lineHeight: 1.55,
                      }}
                    >
                      {item}
                    </li>
                  ))}
                </ul>
              </section>
            </article>
          ))}
        </div>
      </Container>
    </FullWidthSection>
  );
}
