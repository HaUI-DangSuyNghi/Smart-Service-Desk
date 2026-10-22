import { useEffect, useRef, useState, type ElementType, type ReactNode } from "react";
import { cn } from "cn";

interface Props {
  as?: ElementType;
  delay?: number;
  className?: string;
  children: ReactNode;
}

/** Hiện dần phần tử khi cuộn tới. */
export function Reveal({ as: Tag = "div", delay = 0, className, children }: Props) {
  const ref = useRef<HTMLElement>(null);
  const [visible, setVisible] = useState(false);

  useEffect(() => {
    const el = ref.current;
    if (!el) return;
    const io = new IntersectionObserver(
      ([entry]) => {
        if (entry?.isIntersecting) {
          setVisible(true);
          io.unobserve(el);
        }
      },
      { threshold: 0.15 },
    );
    io.observe(el);
    return () => io.disconnect();
  }, []);

  return (
    <Tag
      ref={ref}
      className={cn(
        "transition-all duration-700 ease-out",
        visible ? "translate-y-0 opacity-100" : "translate-y-7 opacity-0",
        className,
      )}
      style={{ transitionDelay: `${delay}ms` }}
    >
      {children}
    </Tag>
  );
}
