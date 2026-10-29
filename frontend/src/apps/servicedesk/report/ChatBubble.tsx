import type { ReactNode } from "react";
import { IconRobot } from "@tabler/icons-react";
import { cn } from "cn";

interface Props {
  from: "bot" | "user";
  children: ReactNode;
}

/** Một tin nhắn: trợ lý bên trái có biểu tượng, người dùng bên phải nền xanh. */
export const ChatBubble = ({ from, children }: Props) => {
  const isBot = from === "bot";
  return (
    <div className={cn("flex items-end gap-2", !isBot && "justify-end")}>
      {isBot && (
        <span
          aria-hidden
          className="grid size-7 shrink-0 place-items-center rounded-full bg-crm-accent-soft text-crm-accent"
        >
          <IconRobot size={16} stroke={2} />
        </span>
      )}
      <div
        className={cn(
          // Chữ người dùng gõ giữ xuống dòng; từ dài không làm tràn khung
          "max-w-[85%] rounded-crm-md px-3 py-2 break-words whitespace-pre-wrap sm:max-w-[75%]",
          isBot ? "bg-crm-subtle text-crm-text ring-1 ring-crm-line" : "bg-crm-accent text-white",
        )}
      >
        {children}
      </div>
    </div>
  );
};
