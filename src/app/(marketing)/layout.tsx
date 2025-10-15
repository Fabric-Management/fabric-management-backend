// src/app/(marketing)/layout.tsx

import { PropsWithChildren } from "react";
import { MarketingNav } from "@/ui/components/navigation/MarketingNav";
import { MarketingFooter } from "@/ui/components/layout/MarketingFooter";
import "@/styles/marketing.css";

export default function MarketingLayout({ children }: PropsWithChildren) {
  return (
    <div className="marketing-shell">
      <MarketingNav />
      <main>{children}</main>
      <MarketingFooter />
    </div>
  );
}
