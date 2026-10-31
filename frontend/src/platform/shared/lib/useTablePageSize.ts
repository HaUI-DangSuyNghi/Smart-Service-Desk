import { useCallback, useRef, useState } from "react";

/** Keep standard 32px table rows within the available panel height. */
export const useTablePageSize = () => {
  const [pageSize, setPageSize] = useState(10);
  const observer = useRef<ResizeObserver | null>(null);
  const tableRef = useCallback((node: HTMLDivElement | null) => {
    observer.current?.disconnect();
    if (!node) return;
    observer.current = new ResizeObserver(([entry]) => {
      if (entry.contentRect.height > 0)
        setPageSize(Math.max(1, Math.min(20, Math.floor((entry.contentRect.height - 50) / 32))));
    });
    observer.current.observe(node);
  }, []);
  return { pageSize, tableRef };
};
