// fabric-management-frontend/src/ui/components/navigation/MarketingNav.tsx

"use client";

import Link from "next/link";
import { useState } from "react";
import clsx from "clsx";
import { Container } from "@/ui/components/layout/Container";
import { colorTokens, layoutTokens, typographyTokens } from "@/ui/theme/tokens";
import { marketingNavLinks } from "@/domains/marketing/content/navigation";

export function MarketingNav() {
  const [isMenuOpen, setMenuOpen] = useState(false);

  return (
    <header
      style={{
        position: "sticky",
        top: 0,
        zIndex: 40,
        backdropFilter: "blur(16px)",
        background: "rgba(248, 249, 251, 0.85)",
        borderBottom: `1px solid ${colorTokens.border.default}`,
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

        <nav className="hidden md:flex items-center gap-8">
          {marketingNavLinks.map((link) => (
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
              background: `linear-gradient(135deg, ${colorTokens.accent.gradientFrom}, ${colorTokens.accent.gradientTo})`,
              color: colorTokens.text.inverse,
              fontWeight: typographyTokens.weight.medium,
              fontSize: "0.95rem",
              boxShadow: colorTokens.shadow.soft,
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
            border: `1px solid ${colorTokens.border.default}`,
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
            borderTop: `1px solid ${colorTokens.border.default}`,
          }}
        >
          <Container className="flex flex-col gap-4 py-6">
            {marketingNavLinks.map((link) => (
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

            <div className="flex flex-col gap-3 pt-4 border-t" style={{ borderColor: colorTokens.border.default }}>
              <Link
                href="/register"
                onClick={() => setMenuOpen(false)}
                style={{
                  padding: "0.75rem 1.25rem",
                  borderRadius: layoutTokens.radius.md,
                  background: `linear-gradient(135deg, ${colorTokens.accent.gradientFrom}, ${colorTokens.accent.gradientTo})`,
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
