import type { Priority, WorkSettings } from "../workService";

/** Người xử lý giả; subject bắt đầu bằng "mock-" để tra tên không gọi backend. */
export const MOCK_PEOPLE: Readonly<Record<string, string>> = {
  "mock-agent-an": "Nguyễn Văn An",
  "mock-agent-binh": "Trần Thị Bình",
  "mock-agent-cuong": "Lê Văn Cường",
  "mock-requester-dung": "Phạm Thị Dung",
  "mock-requester-hai": "Hoàng Văn Hải",
  "mock-coordinator": "Vũ Thị Mai (điều phối)",
  "mock-admin": "Quản trị hệ thống",
};

/** Kỹ thuật viên giả theo tổ (khớp bảng R1 của plan). */
export const MOCK_AGENTS_BY_GROUP: Readonly<Record<string, readonly string[]>> = {
  hardware: ["mock-agent-an", "mock-agent-binh"],
  network: ["mock-agent-cuong", "mock-agent-an"],
  software: ["mock-agent-binh"],
  sysadmin: ["mock-agent-cuong"],
};

export const isMockSubject = (subject: string) => subject.startsWith("mock-");

/** Giá trị mặc định ở mục 6 của plan nghiệp vụ. */
export const DEFAULT_WORK_SETTINGS: WorkSettings = {
  version: 1,
  updatedAt: new Date().toISOString(),
  updatedBy: "mock-admin",
  assignment: {
    pendingLoadFactor: 0.5,
    tieThresholdMinutes: 15,
    maxInProgress: 1,
    minRemainingMinutes: 10,
  },
  shifts: [
    { name: "Sáng", start: "07:00", end: "11:30" },
    { name: "Chiều", start: "13:00", end: "17:00" },
  ],
  sla: {
    targets: {
      CRITICAL: { startWithinMinutes: 15, resolveWithinMinutes: 120 },
      HIGH: { startWithinMinutes: 60, resolveWithinMinutes: 480 },
      MEDIUM: { startWithinMinutes: 240, resolveWithinMinutes: 2880 },
      LOW: { startWithinMinutes: 1440, resolveWithinMinutes: 7200 },
    },
    escalateAfterRatio: 0.5,
    maxEscalatedPriority: "HIGH",
    waitingExternalCounts: true,
  },
  critical: { autoQueueAfterMinutes: 5 },
  alerts: { warnRemainingRatio: 0.2, pendingRemindDays: 3, reopenUnclaimedMinutes: 15 },
  closing: { autoCloseAfterDays: 3 },
  estimate: { minSamples: 20, windowMonths: 6, outlierMinMinutes: 2, outlierMaxMinutes: 480 },
};

/** CRITICAL đứng đầu; dùng để sắp hàng đợi. */
export const PRIORITY_RANK: Readonly<Record<Priority, number>> = {
  CRITICAL: 4,
  HIGH: 3,
  MEDIUM: 2,
  LOW: 1,
};

/** "Nửa giờ trước" → ISO; dùng dựng dữ liệu mẫu theo thời điểm mở trang. */
export const minutesAgo = (minutes: number, now = Date.now()) =>
  new Date(now - minutes * 60 * 1000).toISOString();
