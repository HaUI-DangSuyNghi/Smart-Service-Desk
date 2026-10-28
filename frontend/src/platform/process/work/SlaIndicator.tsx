import { IconClockHour4 } from "@tabler/icons-react";
import { cn } from "cn";
import type { Sla } from "@/platform/services";
import { formatDateTime } from "@/platform/shared/lib/format";
import { slaStatus } from "./slaFormat";
import { useNow } from "./useNow";

interface Props {
  sla: Sla;
  /** Đã bắt đầu làm: đếm tới hạn xong thay cho hạn bắt đầu. */
  started: boolean;
  /** Dạng nhãn có nền theo mức cảnh báo (thẻ Board); mặc định chỉ là chữ có icon. */
  chip?: boolean;
}

// Viết đủ tên class để Tailwind sinh ra được
const TONE_CHIP = {
  red: "bg-crm-tone-red-soft",
  orange: "bg-crm-tone-orange-soft",
  gray: "bg-crm-tone-gray-soft",
} as const;

const TONE_TEXT = {
  red: "text-crm-danger",
  orange: "text-crm-tone-orange",
  gray: "text-crm-text-2",
} as const;

/** Đồng hồ hạn xử lý (R5): đỏ khi quá hạn, cam khi sắp hết; tự cập nhật mỗi phút. */
export const SlaIndicator = ({ sla, started, chip = false }: Props) => {
  const now = useNow();
  const status = slaStatus(sla, started, now);
  const tone = status.tone === "red" || status.tone === "orange" ? status.tone : "gray";

  return (
    <span
      title={`Hạn: ${formatDateTime(status.deadline)}`}
      className={cn(
        "inline-flex items-center gap-1 whitespace-nowrap",
        TONE_TEXT[tone],
        chip && ["h-5 rounded-crm-sm px-1.5 text-crm-sm font-medium", TONE_CHIP[tone]],
      )}
    >
      <IconClockHour4 size={14} stroke={2} className="shrink-0" />
      {status.text}
    </span>
  );
};
