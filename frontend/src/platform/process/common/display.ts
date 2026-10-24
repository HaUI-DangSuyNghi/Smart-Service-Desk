import type { TagTone } from "@/platform/shared/ui/Tag";

// Màu theo nghĩa phổ biến của kết quả; kết quả lạ (quy trình mới) hiện xám
const POSITIVE_OUTCOMES = new Set([
  "approved",
  "accepted",
  "resolved",
  "delivered",
  "done",
  "completed",
]);
const NEGATIVE_OUTCOMES = new Set(["rejected", "declined", "cancelled"]);
const RETURNED_OUTCOMES = new Set(["rework", "returned"]);

export const outcomeTone = (outcome: string): TagTone => {
  if (POSITIVE_OUTCOMES.has(outcome)) return "green";
  if (NEGATIVE_OUTCOMES.has(outcome)) return "red";
  if (RETURNED_OUTCOMES.has(outcome)) return "orange";
  return "gray";
};

/** "lab-a:agent" → "agent" (candidate group của Flowable dạng workspace:vai trò). */
export const groupRole = (group: string) => group.slice(group.indexOf(":") + 1);

/** Mã do server sinh (UUID) rất dài: hiện 8 ký tự đầu, mã đầy đủ để trong title. */
export const shortKey = (key: string) => (key.length > 12 ? key.slice(0, 8) : key);
