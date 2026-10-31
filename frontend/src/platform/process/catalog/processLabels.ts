import type { ProcessDescriptor, ProcessOption, ProcessStartField } from "@/platform/services";

/**
 * Tra nhãn tiếng Việt theo processKey/taskKey; thiếu nhãn thì trả giá trị dự phòng, không bao giờ rỗng.
 * `processKey = null` (nhật ký cũ không ghi quy trình): tra trong mọi quy trình, lấy nhãn đầu tiên khớp.
 */
export interface ProcessLabels {
  processName: (processKey: string) => string;
  /** `fallback`: tên BPMN của việc, dùng khi backend chưa khai báo nhãn cho bước. */
  taskName: (processKey: string | null, taskKey: string, fallback?: string) => string;
  /** Kết quả được chọn khi hoàn thành bước, theo thứ tự backend khai báo. */
  outcomes: (processKey: string, taskKey: string) => ProcessOption[];
  outcomeLabel: (processKey: string | null, outcome: string) => string;
  /** Ô nhập lúc tạo, theo thứ tự backend khai báo; dùng làm nhãn cho nội dung yêu cầu. */
  startFields: (processKey: string) => ProcessStartField[];
}

export const createProcessLabels = (catalog: readonly ProcessDescriptor[]): ProcessLabels => {
  const processes = new Map(catalog.map((process) => [process.processKey, process]));
  // Bước của một quy trình, hoặc của mọi quy trình khi không biết quy trình nào
  const tasksOf = (processKey: string | null) =>
    processKey === null
      ? catalog.flatMap((process) => process.tasks)
      : (processes.get(processKey)?.tasks ?? []);
  const findTask = (processKey: string | null, taskKey: string) =>
    tasksOf(processKey).find((task) => task.key === taskKey);

  return {
    processName: (processKey) => processes.get(processKey)?.name ?? processKey,
    taskName: (processKey, taskKey, fallback) => {
      const name = findTask(processKey, taskKey)?.name;
      // Backend trả name = key khi chưa có nhãn; khi đó tên BPMN dễ đọc hơn key
      return name && name !== taskKey ? name : (fallback ?? taskKey);
    },
    outcomes: (processKey, taskKey) => findTask(processKey, taskKey)?.outcomes ?? [],
    outcomeLabel: (processKey, outcome) =>
      tasksOf(processKey)
        .flatMap((task) => task.outcomes)
        .find((option) => option.key === outcome)?.label ?? outcome,
    startFields: (processKey) => processes.get(processKey)?.startFields ?? [],
  };
};
