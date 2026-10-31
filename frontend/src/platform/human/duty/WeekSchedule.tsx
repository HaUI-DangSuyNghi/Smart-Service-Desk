import { useState, type ReactNode } from "react";
import { IconChevronLeft, IconChevronRight } from "@tabler/icons-react";
import { cn } from "cn";
import type { SchedulePolicy, ShiftSlot } from "@/platform/services";
import { Button } from "@/platform/shared/ui/button";
import { ErrorState } from "@/platform/shared/ui/ErrorState";
import { TableSkeleton } from "@/platform/shared/ui/Skeleton";
import { toast, toastError } from "@/platform/shared/ui/toast";
import { PERMISSIONS } from "../workspace/permissions";
import { usePeopleNames } from "../workspace/usePeopleNames";
import { usePermissions } from "../workspace/usePermissions";
import { AgentCell, CoordinatorCell, OfficeCell, OfficePeopleCell } from "./ScheduleCell";
import { WorkModeSwitch } from "./WorkModeSwitch";
import {
  useCancelSlot,
  useDecideSlot,
  useDutyList,
  useRegisterSlot,
  useSchedulePolicy,
  useShiftSlots,
  useWorkModes,
} from "./useDuty";
import {
  addDays,
  dayMonth,
  holidayName,
  isDayOff,
  shiftLabel,
  shortTime,
  slotErrorMessage,
  slotLockReason,
  WEEKDAY_LABELS,
  weekDays,
  weekStart,
  zonedNow,
} from "./scheduleRules";

type Shift = SchedulePolicy["shifts"][number];

/**
 * Lịch trực theo tuần (R3): kỹ thuật viên bấm ô để đăng ký / huỷ ca của mình; điều phối viên thấy
 * họ tên mọi người trong từng ô, đăng ký hộ, huỷ hộ, duyệt. Điện thoại: mỗi ngày một khối.
 */
export const WeekSchedule = () => {
  const { can } = usePermissions();
  const coordinator = can(PERMISSIONS.taskAssign);
  const policy = useSchedulePolicy();
  const zone = policy.data?.zone ?? "Asia/Ho_Chi_Minh";
  const now = zonedNow(zone);
  const thisWeek = weekStart(now.date);
  const [selectedWeek, setWeek] = useState<string | null>(null);
  const monday = selectedWeek ?? thisWeek;
  const days = weekDays(monday);
  const slots = useShiftSlots(days[0]!, days[6]!, policy.isSuccess);
  const agents = useDutyList(coordinator);
  // Kỹ thuật viên chỉ nhận hình thức của mình; điều phối viên nhận của mọi người
  const workModes = useWorkModes();
  const office = (workModes.data ?? [])
    .filter((item) => item.mode === "OFFICE")
    .map((item) => item.subject);
  const names = usePeopleNames([
    ...(slots.data ?? []).map((slot) => slot.subject),
    ...(agents.data ?? []).map((status) => status.subject),
    ...office,
  ]);
  const register = useRegisterSlot();
  const cancel = useCancelSlot();
  const decide = useDecideSlot();
  const busy = register.isPending || cancel.isPending || decide.isPending;

  const label = (date: string, shift: Shift) =>
    `${shiftLabel(shift.name)} ${WEEKDAY_LABELS[days.indexOf(date)]} ${dayMonth(date)}`;

  const handleRegister = (subject: string, date: string, shift: Shift) =>
    register.mutate(
      { subject, date, shift: shift.name },
      {
        onSuccess: (slot) =>
          toast.success(slot.status === "PENDING" ? "Đã gửi đăng ký, chờ duyệt" : "Đã đăng ký ca", {
            description: label(date, shift),
          }),
        onError: (error) => toastError(slotErrorMessage(error, "Không đăng ký được ca."), error),
      },
    );
  const handleCancel = (slot: ShiftSlot, shift: Shift) =>
    cancel.mutate(
      { subject: coordinator ? slot.subject : "me", slotId: slot.id },
      {
        onSuccess: () => toast.success("Đã huỷ ca", { description: label(slot.date, shift) }),
        onError: (error) => toastError(slotErrorMessage(error, "Không huỷ được ca."), error),
      },
    );
  const handleDecide = (slot: ShiftSlot, decision: "approve" | "reject") =>
    decide.mutate(
      { slotId: slot.id, decision },
      {
        onSuccess: () => toast.success(decision === "approve" ? "Đã duyệt ca" : "Đã từ chối ca"),
        onError: (error) => toastError(slotErrorMessage(error, "Không cập nhật được ca."), error),
      },
    );

  if (policy.error)
    return <ErrorState error={policy.error} fallback="Không tải được quy tắc ca trực." />;
  if (!policy.data) return <TableSkeleton columns={8} label="Đang tải lịch trực" />;
  const rules = policy.data;
  const pending = (slots.data ?? []).filter((slot) => slot.status === "PENDING").length;

  const cell = (date: string, shift: Shift): ReactNode => {
    const dayOff = isDayOff(rules, date);
    // Ngày làm việc: người làm hành chính đã nằm ở hàng "Hành chính", không lặp lại ô ca (dữ liệu cũ)
    const inCell = (slots.data ?? []).filter(
      (slot) =>
        slot.date === date &&
        slot.shift === shift.name &&
        (dayOff || !coordinator || !office.includes(slot.subject)),
    );
    const lock = slotLockReason(rules, date, shift.end, coordinator, now);
    // Người làm hành chính tự trực ngày làm việc: không đăng ký (backend cũng chặn)
    if (!coordinator && office.length > 0 && !dayOff) return <OfficeCell />;
    return coordinator ? (
      <CoordinatorCell
        slots={inCell}
        agents={(agents.data ?? [])
          .map((status) => status.subject)
          .filter((subject) => dayOff || !office.includes(subject))}
        names={names}
        lock={lock}
        busy={busy}
        onRegister={(subject) => handleRegister(subject, date, shift)}
        onCancel={(slot) => handleCancel(slot, shift)}
        onDecide={handleDecide}
      />
    ) : (
      <AgentCell
        slot={inCell[0]}
        lock={lock}
        busy={busy}
        onRegister={() => handleRegister("me", date, shift)}
        onCancel={(slot) => handleCancel(slot, shift)}
      />
    );
  };

  // Hàng "Hành chính" (điều phối viên): giờ hành chính là từ đầu ca đầu tới hết ca cuối
  const showOffice = coordinator && office.length > 0;
  // Số hàng dưới tiêu đề ngày: các ca, thêm hàng Hành chính nếu có
  const rowCount = rules.shifts.length + (showOffice ? 1 : 0);
  const officeStart = rules.shifts.map((shift) => shortTime(shift.start)).sort()[0];
  const ends = rules.shifts.map((shift) => shortTime(shift.end)).sort();
  const officeEnd = ends[ends.length - 1];
  const officeHeader = (
    <div className="flex flex-col justify-center px-2 py-1">
      <span className="font-medium">Hành chính</span>
      <span className="text-crm-xs text-crm-text-3 tabular-nums">
        {officeStart}–{officeEnd}
      </span>
    </div>
  );
  const officeCell = (date: string) =>
    isDayOff(rules, date) ? null : <OfficePeopleCell people={office} names={names} />;

  // Ngày lễ: ghi chú tên lễ dưới ngày
  const holidayNote = (date: string) => {
    const holiday = holidayName(rules, date);
    return holiday ? (
      <span title={holiday} className="block truncate text-crm-xs text-crm-tone-red">
        {holiday}
      </span>
    ) : null;
  };

  const shiftHeader = (shift: Shift) => (
    <div className="flex flex-col justify-center px-2 py-1">
      <span className="font-medium">{shiftLabel(shift.name)}</span>
      <span className="text-crm-xs text-crm-text-3 tabular-nums">
        {shortTime(shift.start)}–{shortTime(shift.end)}
      </span>
    </div>
  );

  return (
    <section className="flex min-h-full flex-1 flex-col gap-3">
      <div className="flex flex-wrap items-center gap-2">
        <Button
          variant="outline"
          size="icon-sm"
          aria-label="Tuần trước"
          onClick={() => setWeek(addDays(monday, -7))}
        >
          <IconChevronLeft size={16} stroke={2} />
        </Button>
        <span className="min-w-36 text-center font-medium tabular-nums">
          {dayMonth(days[0]!)} – {dayMonth(days[6]!)}/{days[6]!.slice(0, 4)}
        </span>
        <Button
          variant="outline"
          size="icon-sm"
          aria-label="Tuần sau"
          onClick={() => setWeek(addDays(monday, 7))}
        >
          <IconChevronRight size={16} stroke={2} />
        </Button>
        {monday !== thisWeek && (
          <Button variant="ghost" size="sm" onClick={() => setWeek(null)}>
            Tuần này
          </Button>
        )}
        {/* Bên phải: số ca chờ duyệt (điều phối viên) và hình thức làm việc của mình */}
        <div className="ml-auto flex flex-wrap items-center gap-3">
          {coordinator && pending > 0 && (
            <span className="rounded-crm-sm bg-crm-tone-orange-soft px-2 py-0.5 text-crm-sm text-crm-tone-orange">
              Chờ duyệt: {pending}
            </span>
          )}
          <WorkModeSwitch />
        </div>
      </div>

      {slots.error && <ErrorState error={slots.error} fallback="Không tải được lịch trực." />}

      {/* Màn rộng: lưới 7 ngày × các ca */}
      {/* Kéo hết chiều cao trang: hàng tiêu đề vừa nội dung, các hàng ca chia đều phần còn lại */}
      <div className="hidden flex-1 flex-col overflow-x-auto md:flex">
        <div
          className="grid min-w-190 flex-1 grid-cols-[96px_repeat(7,minmax(0,1fr))] gap-px rounded-crm-sm border border-crm-line bg-crm-line"
          style={{ gridTemplateRows: `auto repeat(${rowCount}, minmax(min-content, 1fr))` }}
        >
          <div className="bg-crm-sidebar" />
          {days.map((date, index) => (
            <div
              key={date}
              className={cn(
                "bg-crm-sidebar px-2 py-1.5 text-center",
                date === now.date && "font-semibold text-crm-accent",
              )}
            >
              <span className="block text-crm-sm">{WEEKDAY_LABELS[index]}</span>
              <span className="text-crm-xs text-crm-text-3 tabular-nums">{dayMonth(date)}</span>
              {holidayNote(date)}
            </div>
          ))}
          {showOffice && (
            <ShiftRow header={officeHeader}>
              {days.map((date) => (
                <div
                  key={date}
                  className={cn("bg-crm-surface p-1", date === now.date && "bg-crm-accent-soft/40")}
                >
                  {officeCell(date)}
                </div>
              ))}
            </ShiftRow>
          )}
          {rules.shifts.map((shift) => (
            <ShiftRow key={shift.name} header={shiftHeader(shift)}>
              {days.map((date) => (
                <div
                  key={date}
                  className={cn("bg-crm-surface p-1", date === now.date && "bg-crm-accent-soft/40")}
                >
                  {cell(date, shift)}
                </div>
              ))}
            </ShiftRow>
          ))}
        </div>
      </div>

      {/* Điện thoại: mỗi ngày một khối, các ca xếp dọc */}
      <div className="flex flex-col gap-2 md:hidden">
        {days.map((date, index) => (
          <div key={date} className="rounded-crm-sm border border-crm-line">
            <div
              className={cn(
                "border-b border-crm-line bg-crm-sidebar px-3 py-1.5 font-medium",
                date === now.date && "text-crm-accent",
              )}
            >
              {WEEKDAY_LABELS[index]} · {dayMonth(date)}
              {holidayNote(date)}
            </div>
            {showOffice && !isDayOff(rules, date) && (
              <div className="grid grid-cols-[88px_1fr] items-stretch gap-2 p-2">
                {officeHeader}
                {officeCell(date)}
              </div>
            )}
            {rules.shifts.map((shift) => (
              <div key={shift.name} className="grid grid-cols-[88px_1fr] items-stretch gap-2 p-2">
                {shiftHeader(shift)}
                {cell(date, shift)}
              </div>
            ))}
          </div>
        ))}
      </div>
    </section>
  );
};

/** Một hàng của lưới: ô tên ca rồi 7 ô ngày (contents để các ô nằm thẳng trong lưới cha). */
const ShiftRow = ({ header, children }: { header: ReactNode; children: ReactNode }) => (
  <div className="contents">
    <div className="bg-crm-sidebar">{header}</div>
    {children}
  </div>
);
