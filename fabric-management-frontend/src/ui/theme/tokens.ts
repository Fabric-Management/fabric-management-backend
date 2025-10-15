// fabric-management-frontend/src/ui/theme/tokens.ts

export const colorTokens = {
  background: {
    base: "#FFFFFF",
    surface: "#F5F7FB",
    contrast: "#0F172A",
    muted: "#E2E8F0",
    footer: "#111827",
  },
  text: {
    primary: "#0F172A",
    secondary: "#1F2937",
    tertiary: "#4B5563",
    inverse: "#F9FAFB",
  },
  accent: {
    primary: "#2563EB",
    subtle: "#E3ECFF",
  },
} as const;

export const typographyTokens = {
  fontFamily: {
    sans: '"Inter", "Manrope", system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif',
  },
  sizes: {
    hero: "clamp(2.75rem, 6vw, 4rem)",
    subtitle: "clamp(1.1rem, 2vw, 1.5rem)",
    sectionTitle: "clamp(2rem, 4vw, 3rem)",
    body: "1rem",
    overline: "0.75rem",
  },
  weight: {
    regular: 400,
    medium: 500,
    semibold: 600,
    bold: 700,
  },
  letterSpacing: {
    relaxed: "0.02em",
  },
} as const;

export const layoutTokens = {
  maxWidth: "1400px",
  sectionPadding: {
    mobile: "64px",
    desktop: "120px",
  },
  radius: {
    xl: "24px",
    lg: "16px",
    md: "12px",
  },
  gap: {
    sm: "16px",
    md: "24px",
    lg: "40px",
    xl: "64px",
  },
} as const;

export const fabricodeThemeTokens = {
  color: colorTokens,
  typography: typographyTokens,
  layout: layoutTokens,
};

export type FabricodeThemeTokens = typeof fabricodeThemeTokens;
