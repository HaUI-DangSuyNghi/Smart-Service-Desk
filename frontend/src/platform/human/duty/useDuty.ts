import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  cancelShiftSlot,
  decideShiftSlot,
  getSchedulePolicy,
  listDuty,
  listShiftSlots,
  listWorkModes,
  registerShiftSlot,
  setMyDuty,
  setWorkMode,
} from "@/platform/services";
import type { WorkModeKind } from "@/platform/services";
import { useRealtimeInvalidation, workspaceTopic } from "@/platform/shared/realtime";
import { useCurrentWorkspace } from "../workspace/useCurrentWorkspace";

const dutyKey = (workspace: string | null) => ["duty", workspace] as const;

// Ca trực, đăng ký ca, trạng thái trực đổi (của người khác) thì tải lại
const useDutyRealtime = (workspace: string | null) =>
  useRealtimeInvalidation(workspace ? workspaceTopic(workspace, "work") : null, () => [
    dutyKey(workspace),
  ]);

/** Trạng thái trực trong workspace; kỹ thuật viên chỉ nhận về của mình. */
export const useDutyList = (enabled = true) => {
  const { current } = useCurrentWorkspace();
  useDutyRealtime(enabled ? current : null);
  return useQuery({
    queryKey: dutyKey(current),
    // `enabled` bảo đảm current khác null khi queryFn chạy
    queryFn: ({ signal }) => listDuty(current!, signal),
    enabled: current !== null && enabled,
  });
};

/** Bật/tắt trực của chính mình (R3): tắt là nghỉ đột xuất, kể cả đang trong ca đã đăng ký. */
export const useSetMyDuty = () => {
  const { current } = useCurrentWorkspace();
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (onDuty: boolean) => {
      if (!current) throw new Error("Chưa chọn workspace");
      return setMyDuty(current, { onDuty });
    },
    onSettled: () => queryClient.invalidateQueries({ queryKey: dutyKey(current) }),
  });
};

/** Quy tắc đăng ký ca (các ca, hạn chót trong ngày, T7/CN, cần duyệt). */
export const useSchedulePolicy = (enabled = true) => {
  const { current } = useCurrentWorkspace();
  return useQuery({
    queryKey: [...dutyKey(current), "policy"],
    queryFn: ({ signal }) => getSchedulePolicy(current!, signal),
    enabled: current !== null && enabled,
  });
};

/** Ô ca trong một tuần; kỹ thuật viên chỉ nhận ô của mình, điều phối viên nhận tất cả. */
export const useShiftSlots = (from: string, to: string, enabled = true) => {
  const { current } = useCurrentWorkspace();
  useDutyRealtime(enabled ? current : null);
  return useQuery({
    queryKey: [...dutyKey(current), "slots", from, to],
    queryFn: ({ signal }) => listShiftSlots(current!, { from, to }, signal),
    enabled: current !== null && enabled,
  });
};

// Đăng ký / huỷ / duyệt đổi cả lịch lẫn trạng thái trực: tải lại cả nhóm "duty"
const useSlotMutation = <Input, Output>(
  action: (workspace: string, input: Input) => Promise<Output>,
) => {
  const { current } = useCurrentWorkspace();
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: Input) => {
      if (!current) throw new Error("Chưa chọn workspace");
      return action(current, input);
    },
    onSettled: () => queryClient.invalidateQueries({ queryKey: dutyKey(current) }),
  });
};

/** Đăng ký một ô; subject "me" là của mình, điều phối viên truyền subject để đăng ký hộ. */
export const useRegisterSlot = () =>
  useSlotMutation((workspace, input: { subject: string; date: string; shift: string }) =>
    registerShiftSlot(workspace, input.subject, { date: input.date, shift: input.shift }),
  );

export const useCancelSlot = () =>
  useSlotMutation((workspace, input: { subject: string; slotId: string }) =>
    cancelShiftSlot(workspace, input.subject, input.slotId),
  );

export const useDecideSlot = () =>
  useSlotMutation((workspace, input: { slotId: string; decision: "approve" | "reject" }) =>
    decideShiftSlot(workspace, input.slotId, input.decision),
  );

/** Hình thức làm việc (Theo ca / Hành chính); kỹ thuật viên chỉ nhận của mình. */
export const useWorkModes = (enabled = true) => {
  const { current } = useCurrentWorkspace();
  return useQuery({
    queryKey: [...dutyKey(current), "work-modes"],
    queryFn: ({ signal }) => listWorkModes(current!, signal),
    enabled: current !== null && enabled,
  });
};

/** Đổi hình thức làm việc của một kỹ thuật viên (điều phối viên); trạng thái trực đổi theo. */
export const useSetWorkMode = () =>
  useSlotMutation((workspace, input: { subject: string; mode: WorkModeKind }) =>
    setWorkMode(workspace, input.subject, input.mode),
  );
