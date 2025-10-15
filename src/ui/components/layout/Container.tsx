// src/ui/components/layout/Container.tsx

import { CSSProperties, PropsWithChildren } from "react";
import clsx from "clsx";
import { layoutTokens } from "@/ui/theme/tokens";

type ContainerProps = PropsWithChildren<{
  className?: string;
  style?: CSSProperties;
}>;

export function Container({ children, className, style }: ContainerProps) {
  return (
    <div
      className={clsx(
        "w-full",
        className,
      )}
      style={{
        margin: "0 auto",
        paddingLeft: "clamp(1.5rem, 4vw, 3rem)",
        paddingRight: "clamp(1.5rem, 4vw, 3rem)",
        maxWidth: layoutTokens.maxWidth,
        ...style,
      }}
    >
      {children}
    </div>
  );
}
