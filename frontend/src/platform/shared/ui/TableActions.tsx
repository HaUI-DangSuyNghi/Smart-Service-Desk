import type { ReactNode } from "react";

/** One action fills the cell; multiple actions share its width equally. */
export const TableActions = ({ children }: { children: ReactNode }) => (
  <div className="grid w-full grid-flow-col auto-cols-fr items-center gap-2 [&>button]:w-full">
    {children}
  </div>
);
