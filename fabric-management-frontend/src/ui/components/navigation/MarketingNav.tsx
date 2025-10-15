// fabric-management-frontend/src/ui/components/navigation/MarketingNav.tsx

"use client";

import Link from "next/link";
import { useState } from "react";
import clsx from "clsx";
import { Container } from "@/ui/components/layout/Container";
import { colorTokens, layoutTokens, typographyTokens } from "@/ui/theme/tokens";
import { marketingNavLinks } from "@/domains/marketing/content/navigation";
import { fabricodeModules } from "@/domains/marketing/content/modules";

export function MarketingNav() {
  const [isMenuOpen, setMenuOpen] = useState(false);
  const [showModulesPreview, setShowModulesPreview] = useState(false);
  const desktopLinks = marketingNavLinks.filter((link) => link.label !== "Modules");
  const modulesPreview = fabricodeModules.slice(0, 4);

  return (
    <header
      style={{
        position: "sticky",
        top: 0,
        zIndex: 40,
        backdropFilter: "blur(16px)",
        background: "rgba(255, 255, 255, 0.9)",
        borderBottom: `1px solid ${colorTokens.background.muted}`,
      }}
    >
      <Container
        className="flex items-center justify-between"
        style={{ minHeight: "80px" }}
      >
        <Link
          href="/"
          className="font-semibold"
          style={{
            fontSize: "1.15rem",
            letterSpacing: typographyTokens.letterSpacing.relaxed,
          }}
        >
          Fabricode OS
        </Link>

        <nav className="hidden md:flex items-center gap-10">
          <div
            onMouseEnter={() => setShowModulesPreview(true)}
            onMouseLeave={() => setShowModulesPreview(false)}
            style={{ position: "relative" }}
          >
            <button
              type="button"
              style={{
                background: "transparent",
                border: "none",
                fontSize: "0.95rem",
                color: colorTokens.text.secondary,
                cursor: "pointer",
              }}
            >
              Modules
            </button>

            {showModulesPreview && (
              <div
                style={{
                  position: "absolute",
                  top: "2.75rem",
                  left: 0,
                  display: "grid",
                  gridTemplateColumns: "repeat(2, minmax(220px, 1fr))",
                  gap: "1.5rem",
                  background: colorTokens.background.surface,
                  padding: "1.75rem",
                  borderRadius: layoutTokens.radius.lg,
                  border: `1px solid ${colorTokens.background.muted}`,
                  minWidth: "480px",
                }}
              >
                {modulesPreview.map((module) => {
                  const firstSentence = module.description.split(".")[0]?.trim() ?? module.description;
                  const previewText = firstSentence.endsWith(".") ? firstSentence : `${firstSentence}.`;

                  return (
                    <div key={module.id} style={{ display: "flex", flexDirection: "column", gap: "0.6rem" }}>
                      <div style={{ display: "flex", alignItems: "center", gap: "0.6rem" }}>
                        <span aria-hidden style={{ fontSize: "1.4rem" }}>
                          {module.icon}
                        </span>
                        <div style={{ display: "flex", flexDirection: "column" }}>
                          <span
                            style={{
                              fontSize: "0.85rem",
                              letterSpacing: "0.14em",
                              textTransform: "uppercase",
                              color: colorTokens.text.tertiary,
                              fontWeight: typographyTokens.weight.medium,
                            }}
                          >
                            {module.name}
                          </span>
                          <span
                            style={{
                              fontSize: "1rem",
                              fontWeight: typographyTokens.weight.semibold,
                              color: colorTokens.text.primary,
                            }}
                          >
                            {module.title}
                          </span>
                        </div>
                      </div>
                      <p
                        style={{
                          fontSize: "0.95rem",
                          color: colorTokens.text.tertiary,
                          lineHeight: 1.5,
                        }}
                      >
                        {previewText}
                      </p>
                    </div>
                  );
                })}
              </div>
            )}
          </div>

          {desktopLinks.map((link) => (
            <Link
              key={link.href}
              href={link.href}
              className="transition-colors"
              style={{
                fontSize: "0.95rem",
                color: colorTokens.text.secondary,
              }}
            >
              {link.label}
            </Link>
          ))}
        </nav>

        <div className="hidden md:flex items-center gap-3">
          <Link
            href="/register"
            className="rounded-full transition-transform"
            style={{
              padding: "0.65rem 1.4rem",
              background: colorTokens.accent.primary,
              color: colorTokens.text.inverse,
              fontWeight: typographyTokens.weight.medium,
              fontSize: "0.95rem",
            }}
          >
            Get Now
          </Link>
        </div>

        <button
          type="button"
          className="md:hidden -mr-1"
          aria-label="Toggle navigation"
          onClick={() => setMenuOpen((prev) => !prev)}
          style={{
            width: "44px",
            height: "44px",
            display: "grid",
            placeItems: "center",
            borderRadius: layoutTokens.radius.md,
            border: `1px solid ${colorTokens.background.muted}`,
          }}
        >
          <span
            className={clsx(
              "block h-0.5 w-5 rounded-full transition-transform duration-300",
              isMenuOpen && "translate-y-1.5 rotate-45",
            )}
            style={{ backgroundColor: colorTokens.text.primary }}
          />
          <span
            className={clsx(
              "block h-0.5 w-5 rounded-full transition-opacity duration-300",
              isMenuOpen ? "opacity-0" : "opacity-100",
            )}
            style={{ backgroundColor: colorTokens.text.primary, marginTop: "6px", marginBottom: "6px" }}
          />
          <span
            className={clsx(
              "block h-0.5 w-5 rounded-full transition-transform duration-300",
              isMenuOpen && "-translate-y-1.5 -rotate-45",
            )}
            style={{ backgroundColor: colorTokens.text.primary }}
          />
        </button>
      </Container>

      {isMenuOpen && (
        <div
          className="md:hidden"
          style={{
            background: colorTokens.background.surface,
            borderTop: `1px solid ${colorTokens.background.muted}`,
          }}
        >
          <Container className="flex flex-col gap-4 py-6">
            <details>
              <summary
                style={{
                  fontSize: "1rem",
                  color: colorTokens.text.primary,
                  cursor: "pointer",
                }}
              >
                Modules
              </summary>
              <ul style={{ display: "flex", flexDirection: "column", gap: "0.6rem", marginTop: "0.75rem" }}>
                {modulesPreview.map((module) => (
                  <li key={module.id} style={{ color: colorTokens.text.secondary, fontSize: "0.95rem" }}>
                    <span style={{ fontWeight: typographyTokens.weight.medium }}>{module.name}</span> — {module.title}
                  </li>
                ))}
                <li>
                  <Link
                    href="#modules"
                    onClick={() => setMenuOpen(false)}
                    style={{ color: colorTokens.accent.primary, fontSize: "0.95rem" }}
                  >
                    View full module library
                  </Link>
                </li>
              </ul>
            </details>

            {desktopLinks.map((link) => (
              <Link
                key={link.href}
                href={link.href}
                onClick={() => setMenuOpen(false)}
                style={{
                  fontSize: "1rem",
                  color: colorTokens.text.primary,
                }}
              >
                {link.label}
              </Link>
            ))}

            <div className="flex flex-col gap-3 pt-4 border-t" style={{ borderColor: colorTokens.background.muted }}>
              <Link
                href="/register"
                onClick={() => setMenuOpen(false)}
                style={{
                  padding: "0.75rem 1.25rem",
                  borderRadius: layoutTokens.radius.md,
                  background: colorTokens.accent.primary,
                  color: colorTokens.text.inverse,
                  fontWeight: typographyTokens.weight.medium,
                  textAlign: "center",
                }}
              >
                Get Now
              </Link>
            </div>
          </Container>
        </div>
      )}
    </header>
  );
}
