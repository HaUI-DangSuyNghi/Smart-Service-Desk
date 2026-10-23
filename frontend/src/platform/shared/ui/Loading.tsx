import { useEffect, useState } from "react";
import { LOADING_MIN_MS } from "../lib/minDuration";

interface Props {
  active?: boolean;
  minMs?: number;
  label?: string;
}

export const Loading = ({ active = true, minMs = LOADING_MIN_MS, label = "Đang tải" }: Props) => {
  const [holding, setHolding] = useState(active);
  const [prevActive, setPrevActive] = useState(active);

  // Vừa bật lại: bắt đầu một lượt giữ mới (cập nhật state theo prop ngay trong render)
  if (active !== prevActive) {
    setPrevActive(active);
    if (active) setHolding(true);
  }

  useEffect(() => {
    if (!holding) return;
    const timer = setTimeout(() => setHolding(false), minMs);
    return () => clearTimeout(timer);
  }, [holding, minMs]);

  if (!active && !holding) return null;
  return (
    <div
      role="status"
      aria-label={label}
      className="absolute inset-0 z-10 grid place-items-center bg-black/50 backdrop-blur-sm"
    >
      <span className="bar-loader text-white" />
    </div>
  );
};
