const DATE_TIME = new Intl.DateTimeFormat("vi-VN", { dateStyle: "short", timeStyle: "short" });

/** "2026-10-24T08:00:00Z" → "24/10/2026, 15:00" theo giờ máy người dùng. */
export const formatDateTime = (iso: string) => DATE_TIME.format(new Date(iso));

const RELATIVE = new Intl.RelativeTimeFormat("vi-VN", { numeric: "auto" });
const MINUTE = 60;
const HOUR = 60 * MINUTE;
const DAY = 24 * HOUR;

/** "5 phút trước", "hôm qua"; cũ hơn 7 ngày thì hiện ngày giờ đầy đủ. `now` để test. */
export const formatRelativeTime = (iso: string, now = Date.now()) => {
  const seconds = Math.round((new Date(iso).getTime() - now) / 1000);
  const distance = Math.abs(seconds);
  if (distance < MINUTE) return "vừa xong";
  if (distance >= 7 * DAY) return formatDateTime(iso);
  if (distance >= DAY) return RELATIVE.format(Math.round(seconds / DAY), "day");
  if (distance >= HOUR) return RELATIVE.format(Math.round(seconds / HOUR), "hour");
  return RELATIVE.format(Math.round(seconds / MINUTE), "minute");
};

/** Khoảng thời gian giữa hai mốc: "dưới 1 phút", "15 phút", "2 giờ 5 phút", "1 ngày 3 giờ". */
export const formatDuration = (fromIso: string, toIso: string) => {
  const minutes = Math.floor((new Date(toIso).getTime() - new Date(fromIso).getTime()) / 60_000);
  if (minutes < 1) return "dưới 1 phút";

  const days = Math.floor(minutes / (24 * 60));
  const hours = Math.floor((minutes % (24 * 60)) / 60);
  const rest = minutes % 60;
  const parts: string[] = [];
  if (days > 0) parts.push(`${days} ngày`);
  if (hours > 0) parts.push(`${hours} giờ`);
  // Đã tính theo ngày thì bỏ phút cho gọn
  if (rest > 0 && days === 0) parts.push(`${rest} phút`);
  return parts.join(" ");
};

/** 1536 → "1,5 KB". */
export const formatFileSize = (bytes: number) => {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024)
    return `${(bytes / 1024).toLocaleString("vi-VN", { maximumFractionDigits: 1 })} KB`;
  return `${(bytes / 1024 / 1024).toLocaleString("vi-VN", { maximumFractionDigits: 1 })} MB`;
};
