import { LifeBuoy } from "lucide-react";
import { cn } from "cn";

/** Logo chữ thay cho logo ảnh. */
export function Wordmark({ light }: { light: boolean }) {
  return (
    <span
      className={cn(
        "flex items-center gap-2 text-xl font-black tracking-tight",
        light ? "text-white" : "text-(--landing-text)",
      )}
    >
      <span className="grid size-8 place-items-center rounded-lg bg-(--landing-accent) text-white">
        <LifeBuoy className="size-5" />
      </span>
      Smart Service Desk
    </span>
  );
}
