import type { ProcessCount } from "@/platform/services";

/** Các khoảng thời gian chọn nhanh trên dashboard (số ngày tính cả hôm nay). */
export const PERIODS = [
  { key: "7", label: "7 ngày", days: 7 },
  { key: "30", label: "30 ngày", days: 30 },
  { key: "90", label: "90 ngày", days: 90 },
] as const;

export type PeriodKey = (typeof PERIODS)[number]["key"];

export interface DateRange {
  /** ISO, tính cả: 0 giờ của ngày đầu (giờ máy người xem). */
  from: string;
  /** ISO, không tính: 0 giờ ngày mai. */
  to: string;
}

/** `days` ngày gần nhất tính cả hôm nay, ranh giới theo giờ máy người xem. `now` để test. */
export const lastDays = (days: number, now = new Date()): DateRange => {
  const start = new Date(now.getFullYear(), now.getMonth(), now.getDate() - (days - 1));
  const end = new Date(now.getFullYear(), now.getMonth(), now.getDate() + 1);
  return { from: start.toISOString(), to: end.toISOString() };
};

/** Múi giờ của máy người xem, để backend chia số liệu theo ngày giống ngày người xem thấy. */
export const viewerZone = () => Intl.DateTimeFormat().resolvedOptions().timeZone;

export interface DashboardTotals {
  started: number;
  running: number;
  completed: number;
  waitingClaim: number;
  inProgress: number;
  /** Trung bình có trọng số theo số yêu cầu đã xong; null khi chưa có yêu cầu nào xong. */
  averageCompletionSeconds: number | null;
}

/** Cộng số liệu mọi quy trình thành các thẻ tổng trên đầu dashboard. */
export const summarize = (processes: readonly ProcessCount[]): DashboardTotals => {
  const totals = processes.reduce(
    (sum, process) => ({
      started: sum.started + process.started,
      running: sum.running + process.running,
      completed: sum.completed + process.completed,
      waitingClaim: sum.waitingClaim + process.waitingClaim,
      inProgress: sum.inProgress + process.inProgress,
      // Trung bình từng quy trình nhân số yêu cầu đã xong để ra tổng thời gian
      completionSeconds:
        sum.completionSeconds + (process.averageCompletionSeconds ?? 0) * process.completed,
    }),
    { started: 0, running: 0, completed: 0, waitingClaim: 0, inProgress: 0, completionSeconds: 0 },
  );
  const { completionSeconds, ...counts } = totals;
  return {
    ...counts,
    averageCompletionSeconds:
      counts.completed > 0 ? Math.round(completionSeconds / counts.completed) : null,
  };
};
