import type { ProcessLabels } from "@/platform/process";
import type { ActivityEntry } from "@/platform/services";

export type ActivityKind = "request" | "claim" | "complete" | "document" | "file" | "other";

/** Một dòng hoạt động đã dịch sang câu tiếng Việt. */
export interface ActivityView {
  kind: ActivityKind;
  title: string;
  /** Dòng phụ: tên quy trình, kết quả... */
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
export const describeActivity = (entry: ActivityEntry, labels: ProcessLabels): ActivityView => {
  const processName = entry.processKey ? labels.processName(entry.processKey) : null;
  const processHref = entry.processInstanceId ? `/processes/${entry.processInstanceId}` : null;

  switch (entry.action) {
    case "process.start":
      return { kind: "request", title: "Tạo yêu cầu", detail: processName, href: processHref, outcome: null };
    case "task.claim":
      return { kind: "claim", title: "Nhận việc", detail: processName, href: processHref, outcome: null };
    case "task.complete": {
      const { taskKey, outcome } = entry.details;
      const taskName = taskKey && entry.processKey ? labels.taskName(entry.processKey, taskKey) : taskKey;
      const outcomeLabel = outcome && entry.processKey ? labels.outcomeLabel(entry.processKey, outcome) : outcome;
      return {
        kind: "complete",
        title: taskName ? `Hoàn thành bước ${taskName}` : "Hoàn thành bước",
        detail: joinParts(processName, outcomeLabel),
        href: processHref,
        outcome: outcome ?? null,
      };
    }
    case "metadata.create":
      return { kind: "document", title: "Tạo tài liệu", detail: null, href: `/knowledge/${entry.resource}`, outcome: null };
    case "file.upload":
      return { kind: "file", title: "Tải file lên", detail: null, href: null, outcome: null };
    case "file.link":
      return { kind: "file", title: "Gắn file vào tài liệu", detail: null, href: null, outcome: null };
    default:
      return { kind: "other", title: entry.action, detail: processName, href: processHref, outcome: null };
  }
};
