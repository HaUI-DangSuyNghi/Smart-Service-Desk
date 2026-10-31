import { IconBriefcase, IconCheck, IconHourglass, IconLock, IconPlus } from "@tabler/icons-react";
import { cn } from "cn";
import type { ShiftSlot } from "@/platform/services";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/platform/shared/ui/dropdown-menu";
import { personColorClass } from "@/platform/shared/ui/Avatar";
import { toast } from "@/platform/shared/ui/toast";
import { personName } from "../workspace/people";

const CELL =
  "flex h-full min-h-14 w-full rounded-crm-sm text-crm-sm outline-none focus-visible:ring-2 focus-visible:ring-crm-focus";
const ADD_BUTTON = cn(
  "flex items-center justify-center rounded-crm-sm py-0.5 text-crm-text-3 outline-none",
  "hover:bg-crm-hover hover:text-crm-text focus-visible:ring-2 focus-visible:ring-crm-focus",
);

interface AgentCellProps {
  slot: ShiftSlot | undefined;
  /** Lý do không đăng ký / huỷ được; bấm vào thì hiện cảnh báo. */
  lock: string | null;
  busy: boolean;
  onRegister: () => void;
  onCancel: (slot: ShiftSlot) => void;
}

/** Ô của kỹ thuật viên: bấm ô trống để đăng ký, bấm ô đã đăng ký để huỷ. */
export const AgentCell = ({ slot, lock, busy, onRegister, onCancel }: AgentCellProps) => {
  const handleClick = () => {
    if (lock) {
      toast.warning(lock);
      return;
    }
    if (slot) onCancel(slot);
    else onRegister();
  };
  const pending = slot?.status === "PENDING";

  return (
    <button
      type="button"
      aria-disabled={Boolean(lock) || busy}
      disabled={busy}
      onClick={handleClick}
      title={slot && !lock ? "Bấm để huỷ" : undefined}
      className={cn(
        CELL,
        "items-center justify-center gap-1.5 px-2 font-medium transition-colors duration-100",
        slot && !pending && "bg-crm-tone-green-soft text-crm-tone-green",
        pending && "bg-crm-tone-orange-soft text-crm-tone-orange",
        !slot && "border border-dashed border-crm-line text-crm-text-3",
        !slot && !lock && "hover:border-crm-accent hover:text-crm-accent",
        lock && "cursor-not-allowed opacity-50",
      )}
    >
      {lock && !slot && <IconLock size={14} stroke={2} />}
      {slot && !pending && <IconCheck size={14} stroke={2} />}
      {pending && <IconHourglass size={14} stroke={2} />}
      {!slot && !lock && <IconPlus size={14} stroke={2} />}
      <span className="truncate">
        {slot ? (pending ? "Chờ duyệt" : "Đã đăng ký") : lock ? "Đã khoá" : "Đăng ký"}
      </span>
    </button>
  );
};

interface CoordinatorCellProps {
  slots: readonly ShiftSlot[];
  /** Kỹ thuật viên hiện tại của workspace (để đăng ký hộ). */
  agents: readonly string[];
  names: ReadonlyMap<string, string>;
  lock: string | null;
  busy: boolean;
  onRegister: (subject: string) => void;
  onCancel: (slot: ShiftSlot) => void;
  onDecide: (slot: ShiftSlot, decision: "approve" | "reject") => void;
}

/** Ô của điều phối viên: họ tên người trực; bấm tên để duyệt / huỷ hộ, "+" để đăng ký hộ. */
export const CoordinatorCell = ({
  slots,
  agents,
  names,
  lock,
  busy,
  onRegister,
  onCancel,
  onDecide,
}: CoordinatorCellProps) => {
  const taken = new Set(slots.map((slot) => slot.subject));
  const available = agents.filter((subject) => !taken.has(subject));

  return (
    <div className={cn(CELL, "flex-col gap-1 p-1")}>
      {slots.map((slot) => {
        const pending = slot.status === "PENDING";
        const name = personName(names, slot.subject);
        return (
          <DropdownMenu key={slot.id}>
            <DropdownMenuTrigger
              disabled={busy}
              title={pending ? `${name} · chờ duyệt` : name}
              className={cn(
                "flex w-full min-w-0 items-center gap-1 rounded-crm-sm px-1.5 py-0.5 text-left outline-none",
                "focus-visible:ring-2 focus-visible:ring-crm-focus",
                // Mỗi người một màu (giống avatar); chờ duyệt phân biệt bằng viền đứt + ⏳, không bằng màu
                personColorClass(name),
                pending && "border border-dashed border-current opacity-80",
              )}
            >
              {pending && <IconHourglass size={12} stroke={2} className="shrink-0" />}
              <span className="truncate">{name}</span>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="start" className="min-w-44">
              <DropdownMenuLabel className="truncate">
                {personName(names, slot.subject)}
              </DropdownMenuLabel>
              {pending && (
                <>
                  <DropdownMenuItem onSelect={() => onDecide(slot, "approve")}>
                    Duyệt
                  </DropdownMenuItem>
                  <DropdownMenuItem onSelect={() => onDecide(slot, "reject")}>
                    Từ chối
                  </DropdownMenuItem>
                </>
              )}
              {!pending && (
                <DropdownMenuItem
                  onSelect={() => (lock ? toast.warning(lock) : onCancel(slot))}
                  className={cn(lock && "opacity-50")}
                >
                  Huỷ ca
                </DropdownMenuItem>
              )}
            </DropdownMenuContent>
          </DropdownMenu>
        );
      })}

      {/* Ô đã khoá: nút thường chỉ báo lý do (menu Radix mở ngay khi nhấn xuống, không chặn được) */}
      {lock ? (
        <button
          type="button"
          aria-label="Đăng ký hộ"
          onClick={() => toast.warning(lock)}
          className={cn(ADD_BUTTON, "cursor-not-allowed opacity-40")}
        >
          <IconPlus size={14} stroke={2} />
        </button>
      ) : (
        available.length > 0 && (
          <DropdownMenu>
            <DropdownMenuTrigger disabled={busy} aria-label="Đăng ký hộ" className={ADD_BUTTON}>
              <IconPlus size={14} stroke={2} />
            </DropdownMenuTrigger>
            <DropdownMenuContent align="start" className="max-h-72 min-w-52 overflow-y-auto">
              <DropdownMenuLabel>Đăng ký hộ</DropdownMenuLabel>
              <DropdownMenuSeparator />
              {available.map((subject) => (
                <DropdownMenuItem key={subject} onSelect={() => onRegister(subject)}>
                  <span className="truncate">{personName(names, subject)}</span>
                </DropdownMenuItem>
              ))}
            </DropdownMenuContent>
          </DropdownMenu>
        )
      )}
    </div>
  );
};

/** Ô ngày làm việc của người làm hành chính: đã tự trực, không cần đăng ký. */
export const OfficeCell = () => (
  <div
    className={cn(
      CELL,
      "items-center justify-center gap-1.5 bg-crm-sidebar px-2 font-medium text-crm-text-2",
    )}
  >
    <IconBriefcase size={14} stroke={2} />
    Hành chính
  </div>
);

/** Hàng "Hành chính" của điều phối viên: họ tên những người tự trực ngày này. */
export const OfficePeopleCell = ({
  people,
  names,
}: {
  people: readonly string[];
  names: ReadonlyMap<string, string>;
}) => (
  <div className={cn(CELL, "flex-col gap-1 p-1")}>
    {people.map((subject) => {
      const name = personName(names, subject);
      return (
        <span
          key={subject}
          title={name}
          className={cn("truncate rounded-crm-sm px-1.5 py-0.5", personColorClass(name))}
        >
          {name}
        </span>
      );
    })}
  </div>
);
