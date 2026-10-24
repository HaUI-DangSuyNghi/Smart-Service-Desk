import type { ProcessDescriptor, ProcessOption } from "@/platform/services";

/** Tra nhãn tiếng Việt theo processKey/taskKey; thiếu nhãn thì trả giá trị dự phòng, không bao giờ rỗng. */
export interface ProcessLabels {
  processName: (processKey: string) => string;
  /** `fallback`: tên BPMN của việc, dùng khi backend chưa khai báo nhãn cho bước. */
  taskName: (processKey: string, taskKey: string, fallback?: string) => string;
  /** Kết quả được chọn khi hoàn thành bước, theo thứ tự backend khai báo. */
  outcomes: (processKey: string, taskKey: string) => ProcessOption[];
  outcomeLabel: (processKey: string, outcome: string) => string;
}

export const createProcessLabels = (catalog: readonly ProcessDescriptor[]): ProcessLabels => {
  const processes = new Map(catalog.map((process) => [process.processKey, process]));
  const findTask = (processKey: string, taskKey: string) =>
    processes.get(processKey)?.tasks.find((task) => task.key === taskKey);

  return {
    processName: (processKey) => processes.get(processKey)?.name ?? processKey,
    taskName: (processKey, taskKey, fallback) => {
      const name = findTask(processKey, taskKey)?.name;
      // Backend trả name = key khi chưa có nhãn; khi đó tên BPMN dễ đọc hơn key
      return name && name !== taskKey ? name : (fallback ?? taskKey);
    },
    outcomes: (processKey, taskKey) => findTask(processKey, taskKey)?.outcomes ?? [],
    outcomeLabel: (processKey, outcome) =>
      processes
        .get(processKey)
        ?.tasks.flatMap((task) => task.outcomes)
        .find((option) => option.key === outcome)?.label ?? outcome,
  };
};
