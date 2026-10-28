import { personName, usePeopleNames } from "../../../platform/human/workspace";
import { PRIORITY_LABELS } from "../../../platform/process";
import { formatRelativeTime } from "../../../platform/shared/lib/format";
import type { Priority } from "../../../platform/services";
import type { Correction } from "../services/ticketService";
import { CATEGORY_LABELS } from "./ticketLabels";
import type { Category } from "./ticketRules";

const FIELD_LABELS: Record<Correction["field"], string> = {
  category: "Loại sự cố",
  priority: "Ưu tiên",
};

/** Giá trị lưu dạng mã (HARDWARE, HIGH) → chữ dễ đọc. */
const valueLabel = (field: Correction["field"], value: string) =>
  field === "category"
    ? (CATEGORY_LABELS[value as Category] ?? value)
    : (PRIORITY_LABELS[value as Priority] ?? value);

/** Lịch sử con người sửa kết quả AI và sửa lẫn nhau (R9). */
export const CorrectionsList = ({ corrections }: { corrections: readonly Correction[] }) => {
  const names = usePeopleNames(corrections.map((item) => item.by));
  if (corrections.length === 0) return null;

  return (
    <section className="border-b border-crm-line px-4 py-4">
      <h2 className="mb-2 font-medium">Lịch sử sửa phân loại</h2>
      <ul className="flex flex-col gap-2">
        {corrections.map((item, index) => (
          <li key={`${item.operationId}-${item.field}-${index}`} className="text-crm-sm">
            <p className="text-crm-text">
              {FIELD_LABELS[item.field]}: {valueLabel(item.field, item.previousValue)}
              {item.fromAi && " (AI)"} →{" "}
              <span className="font-medium">{valueLabel(item.field, item.newValue)}</span>
            </p>
            <p className="text-crm-text-3">
              {personName(names, item.by)} · {formatRelativeTime(item.at)}
              {item.reason && ` · ${item.reason}`}
            </p>
          </li>
        ))}
      </ul>
    </section>
  );
};
