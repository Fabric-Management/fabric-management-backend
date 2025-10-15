// fabric-management-frontend/src/app/layout.tsx

import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "Fabricode OS",
  description: "FabricOS — the intelligent operating system for textile manufacturing.",
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en">
      <body>{children}</body>
    </html>
  );
}
