import { Children, isValidElement, useEffect, useState, type ReactNode } from "react";
import { IconCheck, IconCopy } from "@tabler/icons-react";

const readText = (children: ReactNode): string =>
  Children.toArray(children)
    .map((child) =>
      typeof child === "string" || typeof child === "number"
        ? String(child)
        : isValidElement<{ children?: ReactNode }>(child)
          ? readText(child.props.children)
          : "",
    )
    .join("");

export const DocsCodeBlock = ({ children }: { children?: ReactNode }) => {
  const [status, setStatus] = useState<"idle" | "copied" | "failed">("idle");
  const code = Children.toArray(children).find((child) => isValidElement(child));
  const language = isValidElement<{ className?: string }>(code)
    ? code.props.className?.replace("language-", "")
    : undefined;
  useEffect(() => {
    if (status === "idle") return;
    const timer = window.setTimeout(() => setStatus("idle"), 2000);
    return () => window.clearTimeout(timer);
  }, [status]);
  const copy = async () => {
    try {
      await navigator.clipboard.writeText(readText(children).replace(/\n$/, ""));
      setStatus("copied");
    } catch {
      setStatus("failed");
    }
  };
  return (
    <div className="docs-code-block">
      <div className="docs-code-toolbar">
        <span>
          {language === "powershell"
            ? "PowerShell"
            : language === "dotenv"
              ? ".env"
              : language === "text"
                ? "Quy trình"
                : (language ?? "Mã lệnh")}
        </span>
        <button
          type="button"
          onClick={() => void copy()}
          aria-label="Sao chép đoạn mã"
          aria-live="polite"
          className="inline-flex min-h-9 items-center gap-1.5 rounded-md px-2 hover:bg-(--landing-border)"
        >
          {status === "copied" ? <IconCheck size={14} /> : <IconCopy size={14} />}
          {status === "copied"
            ? "Đã sao chép"
            : status === "failed"
              ? "Không sao chép được"
              : "Sao chép"}
        </button>
      </div>
      <pre>{children}</pre>
    </div>
  );
};
