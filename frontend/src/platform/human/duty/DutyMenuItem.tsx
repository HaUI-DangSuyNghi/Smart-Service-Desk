import { IconCircleFilled } from "@tabler/icons-react";
import { cn } from "cn";
import { DropdownMenuItem } from "@/platform/shared/ui/dropdown-menu";
import { toast, toastError } from "@/platform/shared/ui/toast";
import { PERMISSIONS } from "../workspace/permissions";
import { useActor } from "../workspace/useActor";
import { usePermissions } from "../workspace/usePermissions";
import { useDutyList, useSetMyDuty } from "./useDuty";

/**
 * Công tắc Đang trực / Nghỉ trong menu tài khoản (R3). Có lịch ca thì chỉ trực trong ca
 * (công tắc bật mà ngoài ca vẫn tính là nghỉ). Chỉ người xử lý việc mới thấy.
 */
export const DutyMenuItem = () => {
  const { actor } = useActor();
  const { can } = usePermissions();
  const canHandle = can(PERMISSIONS.taskHandle);
  const duty = useDutyList(canHandle);
  const setDuty = useSetMyDuty();
  const mine = duty.data?.find((status) => status.subject === actor?.subject);

  if (!canHandle || !mine) return null;

  const handleToggle = () => {
    setDuty.mutate(!mine.onDuty, {
      onSuccess: (status) =>
        toast.success(
          !status.onDuty
            ? "Bạn đã chuyển sang nghỉ"
            : status.effectiveDuty
              ? "Bạn đang trực"
              : "Đã bật trực",
          {
            description: !status.onDuty
              ? "Việc chưa bắt đầu của bạn sẽ được giao người khác."
              : status.effectiveDuty
                ? "Bạn sẽ được đề xuất nhận việc mới."
                : "Bạn tự online khi vào ca đã đăng ký.",
          },
        ),
      onError: (error) => toastError("Không đổi được trạng thái trực.", error),
    });
  };

  return (
    // Giữ menu mở để thấy trạng thái vừa đổi
    <DropdownMenuItem
      onSelect={(event) => {
        event.preventDefault();
        handleToggle();
      }}
      disabled={setDuty.isPending}
    >
      <IconCircleFilled
        size={10}
        className={cn(mine.onDuty ? "text-crm-tone-green" : "text-crm-text-faint")}
      />
      <span className="flex-1">
        {mine.onDuty ? "Đang trực" : "Đang nghỉ"}
        {mine.onDuty && !mine.effectiveDuty && (
          <span className="block text-crm-xs text-crm-text-3">Ngoài ca trực</span>
        )}
      </span>
      <span className="text-crm-xs text-crm-text-3">
        {mine.onDuty ? "Chuyển sang nghỉ" : "Bật trực"}
      </span>
    </DropdownMenuItem>
  );
};
