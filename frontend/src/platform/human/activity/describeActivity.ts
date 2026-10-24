import type { ProcessLabels } from "@/platform/process";
import type { ActivityEntry } from "@/platform/services";
import { personName } from "../workspace";

export type ActivityKind =
  "request" | "claim" | "assign" | "complete" | "document" | "file" | "other";

/** Một dòng hoạt động đã dịch sang câu tiếng Việt. */
export interface ActivityView {
  kind: ActivityKind;
  title: string;
  /** Dòng phụ: tên quy trình, người được giao... (kết quả không nằm ở đây mà ở `outcome`). */
  detail: string | null;
  /** Trang mở khi bấm; null khi không có trang tương ứng (file chưa gắn tài liệu). */
  href: string | null;
  /** outcome của bước vừa hoàn thành, để tô màu tag. */
  outcome: string | null;
}

const joinParts = (...parts: (string | null | undefined)[]) =>
  parts.filter((part): part is string => Boolean(part)).join(" · ") || null;

/**
 * Dịch thao tác trong audit sang câu dễ hiểu. Thao tác riêng của app (có tiền tố app) chưa có nhãn
 * thì hiện nguyên mã thao tác, vẫn mở được trang tiến độ nếu thuộc quy trình.
 */
export const describeActivity = (
  entry: ActivityEntry,
  labels: ProcessLabels,
  /** subject → tên, để viết "giao cho Trần Thị Bình"; không tra được thì "Không rõ tên". */
  names: ReadonlyMap<string, string> = new Map(),
): ActivityView => {
  const processName = entry.processKey ? labels.processName(entry.processKey) : null;
  const processHref = entry.processInstanceId ? `/processes/${entry.processInstanceId}` : null;

  switch (entry.action) {
    case "process.start":
      return {
        kind: "request",
        title: "Tạo yêu cầu",
        detail: processName,
        href: processHref,
        outcome: null,
      };
    case "task.claim":
      return {
        kind: "claim",
        title: "Nhận việc",
        detail: processName,
        href: processHref,
        outcome: null,
      };
    case "task.assign": {
      const { taskKey, assignee } = entry.details;
      const taskName = taskKey ? labels.taskName(entry.processKey, taskKey) : taskKey;
      const person = assignee ? personName(names, assignee) : null;
      return {
        kind: "assign",
        title: taskName ? `Giao việc ${taskName}` : "Giao việc",
        detail: joinParts(processName, person ? `cho ${person}` : null),
        href: processHref,
        outcome: null,
      };
    }
    case "task.complete": {
      const { taskKey, outcome } = entry.details;
      const taskName = taskKey ? labels.taskName(entry.processKey, taskKey) : taskKey;
      return {
        kind: "complete",
        title: taskName ? `Hoàn thành bước ${taskName}` : "Hoàn thành bước",
        // Kết quả hiện bằng tag màu (trường outcome), không lặp lại trong dòng chữ
        detail: processName,
        href: processHref,
        outcome: outcome ?? null,
      };
    }
    case "metadata.create":
      return {
        kind: "document",
        title: "Tạo tài liệu",
        detail: null,
        href: `/knowledge/${entry.resource}`,
        outcome: null,
      };
    case "file.upload":
      return { kind: "file", title: "Tải file lên", detail: null, href: null, outcome: null };
    case "file.link":
      return {
        kind: "file",
        title: "Gắn file vào tài liệu",
        detail: null,
        href: null,
        outcome: null,
      };
    default:
      return {
        kind: "other",
        title: entry.action,
        detail: processName,
        href: processHref,
        outcome: null,
      };
  }
};
