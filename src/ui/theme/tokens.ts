// src/ui/theme/tokens.ts

export const colorTokens = {
  background: {
    base: "#F8F9FB",
    surface: "#FFFFFF",
    contrast: "#111827",
    muted: "#E5E7EB",
    footer: "#151823",
  },
  text: {
    primary: "#0F172A",
    secondary: "#475569",
    accent: "#1E3A8A",
    inverse: "#F8FAFF",
  },
  accent: {
    primary: "#1E3A8A",
    subtle: "#E0E7FF",
    gradientFrom: "#0F172A",
    gradientTo: "#1E40AF",
  },
  border: {
    default: "#E2E8F0",
    strong: "#CBD5F5",
  },
  shadow: {
    soft: "0 20px 45px rgba(15, 23, 42, 0.12)",
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
  maxWidth: "1200px",
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

export const motionTokens = {
  duration: {
    short: 0.2,
    medium: 0.4,
    long: 0.6,
  },
  easing: {
    standard: [0.2, 0.8, 0.2, 1],
  },
} as const;

export const fabricodeThemeTokens = {
  color: colorTokens,
  typography: typographyTokens,
  layout: layoutTokens,
  motion: motionTokens,
};

export type FabricodeThemeTokens = typeof fabricodeThemeTokens;
