import type { SchedulePolicy } from "@/platform/services";
import { ApiError } from "@/platform/shared/api";

// Lịch tuần đăng ký ca: ngày dạng "yyyy-MM-dd", giờ dạng "HH:mm", theo múi giờ nghiệp vụ của máy chủ

export const WEEKDAY_LABELS = ["T2", "T3", "T4", "T5", "T6", "T7", "CN"] as const;

/** Đăng ký trước tối đa bấy nhiêu ngày (khớp backend). */
export const HORIZON_DAYS = 62;

// Tên ca seed của backend là tiếng Anh; tên khác do quản trị đặt thì giữ nguyên
const SHIFT_LABELS: Record<string, string> = { Morning: "Sáng", Afternoon: "Chiều" };
export const shiftLabel = (name: string) => SHIFT_LABELS[name] ?? name;

/** "07:00:00" hoặc "07:00" → "07:00". */
export const shortTime = (time: string) => time.slice(0, 5);

// Tính ngày bằng UTC để cộng/trừ ngày không lệch do giờ mùa hè của trình duyệt
const toDate = (iso: string) => new Date(`${iso}T00:00:00Z`);
const toIso = (date: Date) => date.toISOString().slice(0, 10);

export const addDays = (iso: string, days: number) => {
  const date = toDate(iso);
  date.setUTCDate(date.getUTCDate() + days);
  return toIso(date);
};

/** Thứ Hai của tuần chứa ngày này. */
export const weekStart = (iso: string) => {
  const day = toDate(iso).getUTCDay(); // 0 = CN
  return addDays(iso, day === 0 ? -6 : 1 - day);
};

export const weekDays = (monday: string) => Array.from({ length: 7 }, (_, i) => addDays(monday, i));

export const isWeekend = (iso: string) => {
  const day = toDate(iso).getUTCDay();
  return day === 0 || day === 6;
};

/** Tên ngày lễ (ghi chú trong Cấu hình) nếu ngày này là ngày lễ. */
export const holidayName = (policy: SchedulePolicy, date: string) =>
  // Backend trước V40 chưa trả holidays: coi như không có ngày lễ
  (policy.holidays ?? []).find((holiday) => holiday.date === date)?.name ?? null;

/** T7, CN hoặc ngày lễ: người làm hành chính nghỉ, đăng ký ca theo cài đặt "ngày nghỉ". */
export const isDayOff = (policy: SchedulePolicy, date: string) =>
  isWeekend(date) || holidayName(policy, date) !== null;

/** "2026-11-02" → "02/11". */
export const dayMonth = (iso: string) => `${iso.slice(8, 10)}/${iso.slice(5, 7)}`;

const daysBetween = (from: string, to: string) =>
  Math.round((toDate(to).getTime() - toDate(from).getTime()) / 86_400_000);

/** Ngày và giờ hiện tại theo múi giờ nghiệp vụ (không theo múi giờ của trình duyệt). */
export const zonedNow = (zone: string, now = new Date()) => {
  const parts = new Intl.DateTimeFormat("en-CA", {
    timeZone: zone,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    hourCycle: "h23",
  }).formatToParts(now);
  const part = (type: Intl.DateTimeFormatPartTypes) =>
    parts.find((item) => item.type === type)?.value ?? "00";
  return {
    date: `${part("year")}-${part("month")}-${part("day")}`,
    time: `${part("hour")}:${part("minute")}`,
  };
};

/**
 * Lý do không đăng ký / huỷ được ô này (null là được), khớp kiểm tra của backend:
 * kỹ thuật viên chỉ trước giờ ca đầu tiên của chính ngày đó; điều phối viên (đăng ký hộ) tới hết ca.
 */
export const slotLockReason = (
  policy: SchedulePolicy,
  date: string,
  shiftEnd: string,
  coordinator: boolean,
  now: { date: string; time: string },
): string | null => {
  const holiday = holidayName(policy, date);
  if (holiday && !policy.weekendRegistration) return `${holiday}: chưa mở đăng ký ngày lễ.`;
  if (isWeekend(date) && !policy.weekendRegistration) return "Chưa mở đăng ký Thứ 7, Chủ nhật.";
  if (date < now.date) return "Ngày đã qua, không đăng ký hay huỷ được.";
  if (daysBetween(now.date, date) > HORIZON_DAYS) {
    return `Chỉ đăng ký trước tối đa ${HORIZON_DAYS} ngày.`;
  }
  if (date > now.date) return null;
  if (coordinator) return now.time >= shortTime(shiftEnd) ? "Ca này đã kết thúc." : null;
  const cutoff = shortTime(policy.cutoff);
  return now.time >= cutoff
    ? `Đã quá ${cutoff} hôm nay: chỉ đăng ký hoặc huỷ được từ ngày mai.`
    : null;
};

// Câu chi tiết của backend (AgentSlotService) → cảnh báo tiếng Việt
const SLOT_ERRORS: Record<string, string> = {
  "Registration closed for this day": "Đã quá hạn đăng ký hoặc huỷ ca của ngày này.",
  "Weekend registration disabled": "Chưa mở đăng ký Thứ 7, Chủ nhật.",
  "Holiday registration disabled": "Chưa mở đăng ký ngày lễ.",
  "Office hours agent": "Người làm hành chính đã tự trực ngày thường, không cần đăng ký.",
  "Shift already registered": "Ca này đã được đăng ký.",
  "Registration horizon exceeded": `Chỉ đăng ký trước tối đa ${HORIZON_DAYS} ngày.`,
  "Pending slot required": "Ca này không còn chờ duyệt.",
};

export const slotErrorMessage = (error: unknown, fallback: string) =>
  (error instanceof ApiError && error.detail && SLOT_ERRORS[error.detail]) || fallback;
