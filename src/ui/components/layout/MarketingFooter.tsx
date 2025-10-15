// src/ui/components/layout/MarketingFooter.tsx

import Link from "next/link";
import { Container } from "@/ui/components/layout/Container";
import { colorTokens, typographyTokens } from "@/ui/theme/tokens";
import { footerLinks, socialLinks } from "@/domains/marketing/content/footer";

export function MarketingFooter() {
  return (
    <footer
      style={{
        background: colorTokens.background.footer,
        color: colorTokens.text.inverse,
        paddingTop: "64px",
        paddingBottom: "48px",
        marginTop: "120px",
      }}
    >
      <Container className="grid gap-12 md:grid-cols-[2fr,1fr,1fr]">
        <div>
          <p
            style={{
              fontWeight: typographyTokens.weight.semibold,
              fontSize: "1.25rem",
            }}
          >
            Fabricode OS
          </p>
          <p
            style={{
              marginTop: "16px",
              maxWidth: "320px",
              color: "rgba(248, 250, 255, 0.68)",
              lineHeight: 1.5,
            }}
          >
            Modular textile management platform that scales with every mill,
            from cotton to finished fabric.
          </p>
        </div>

        <div>
          <p style={{ fontWeight: typographyTokens.weight.medium, marginBottom: "12px" }}>
            Product
          </p>
          <ul className="space-y-3">
            {footerLinks.product.map((link) => (
              <li key={link.href}>
                <Link
                  href={link.href}
                  style={{
                    color: "rgba(248, 250, 255, 0.68)",
                  }}
                >
                  {link.label}
                </Link>
              </li>
            ))}
          </ul>
        </div>

        <div>
          <p style={{ fontWeight: typographyTokens.weight.medium, marginBottom: "12px" }}>
            Company
          </p>
          <ul className="space-y-3">
            {footerLinks.company.map((link) => (
              <li key={link.href}>
                <Link
                  href={link.href}
                  style={{
                    color: "rgba(248, 250, 255, 0.68)",
                  }}
                >
                  {link.label}
                </Link>
              </li>
            ))}
          </ul>
        </div>
      </Container>

      <Container className="mt-16 flex flex-col gap-6 border-t border-white/10 pt-6 md:flex-row md:items-center md:justify-between">
        <p style={{ color: "rgba(248, 250, 255, 0.5)", fontSize: "0.9rem" }}>
          © {new Date().getFullYear()} Fabricode OS. All rights reserved.
        </p>
        <div className="flex gap-4">
          {socialLinks.map((link) => (
            <Link
              key={link.label}
              href={link.href}
              aria-label={link.label}
              style={{
                width: "42px",
                height: "42px",
                borderRadius: "50%",
                background: "rgba(255, 255, 255, 0.08)",
                display: "grid",
                placeItems: "center",
                color: "rgba(248, 250, 255, 0.75)",
              }}
            >
              {link.icon.toUpperCase()}
            </Link>
          ))}
        </div>
      </Container>
    </footer>
  );
}
