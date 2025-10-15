// src/ui/components/sections/FullWidthSection.tsx

import { PropsWithChildren } from "react";
import clsx from "clsx";
import { layoutTokens, colorTokens } from "@/ui/theme/tokens";

type FullWidthSectionProps = PropsWithChildren<{
  subdued?: boolean;
  className?: string;
  id?: string;
}>;

export function FullWidthSection({
  children,
  subdued = false,
  className,
  id,
}: FullWidthSectionProps) {
  return (
    <section
      id={id}
      className={clsx("w-full", className)}
      style={{
        paddingTop: `clamp(${layoutTokens.sectionPadding.mobile}, 10vw, ${layoutTokens.sectionPadding.desktop})`,
        paddingBottom: `clamp(${layoutTokens.sectionPadding.mobile}, 10vw, ${layoutTokens.sectionPadding.desktop})`,
        background: subdued ? colorTokens.background.base : colorTokens.background.surface,
      }}
    >
      {children}
    </section>
  );
}
