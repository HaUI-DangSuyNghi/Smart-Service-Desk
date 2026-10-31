import { IconArrowUp } from "@tabler/icons-react";
import type { Priority } from "@/platform/services";
import { Tag } from "@/platform/shared/ui/Tag";
import { PRIORITY_LABELS, PRIORITY_TONES } from "./workLabels";

interface Props {
  priority: Priority;
  /** Được nâng bậc do chờ lâu (R5): thêm mũi tên lên. */
  escalated?: boolean;
}

/** Mức ưu tiên: Khẩn cấp đỏ, Cao cam, Trung bình xanh, Thấp xám. */
export const PriorityTag = ({ priority, escalated }: Props) => {
  return (
    <span title={escalated ? "Được nâng mức ưu tiên do chờ lâu" : undefined}>
      <Tag tone={PRIORITY_TONES[priority]}>
        {escalated && <IconArrowUp size={12} stroke={2} aria-label="Đã nâng" />}
        {PRIORITY_LABELS[priority]}
      </Tag>
    </span>
  );
};
