import type { Sla } from "@/platform/services";
import { formatSeconds } from "@/platform/shared/lib/format";
import type { TagTone } from "@/platform/shared/ui/Tag";

export interface SlaStatus {
  text: string;
  tone: TagTone;
  /** Mốc hạn đang tính, để hiện ngày giờ đầy đủ. */
  deadline: string;
}

const left = (deadline: string, now: number) =>
  formatSeconds((new Date(deadline).getTime() - now) / 1000);

/**
 * Hạn nào đang quan trọng (R5, R8): chưa bắt đầu thì hạn bắt đầu, đã bắt đầu thì hạn xong.
 * Đỏ khi quá hạn, cam khi còn dưới ngưỡng cảnh báo, xám khi đang dừng tính hạn. `now` để test.
 */
export const slaStatus = (sla: Sla, started: boolean, now = Date.now()): SlaStatus => {
  if (sla.resolveBreached) return { text: "Quá hạn xử lý", tone: "red", deadline: sla.resolveBy };
  if (!started && sla.startBreached) {
    return { text: "Quá hạn bắt đầu", tone: "red", deadline: sla.startBy };
  }
  if (sla.clockPaused) {
    return { text: "Tạm dừng tính hạn", tone: "gray", deadline: sla.resolveBy };
  }
  const tone: TagTone = sla.warning ? "orange" : "gray";
  if (!started) {
    return { text: `Bắt đầu trong ${left(sla.startBy, now)}`, tone, deadline: sla.startBy };
  }
  return { text: `Còn ${left(sla.resolveBy, now)}`, tone, deadline: sla.resolveBy };
};
