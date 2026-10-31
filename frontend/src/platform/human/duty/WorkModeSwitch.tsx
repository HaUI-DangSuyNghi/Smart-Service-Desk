import type { WorkModeKind } from "@/platform/services";
import { toast, toastError } from "@/platform/shared/ui/toast";
import { ViewTabs } from "@/platform/shared/ui/ViewTabs";
import { PERMISSIONS } from "../workspace/permissions";
import { useActor } from "../workspace/useActor";
import { usePermissions } from "../workspace/usePermissions";
import { useSetWorkMode, useWorkModes } from "./useDuty";

const MODE_TABS: readonly { key: WorkModeKind; label: string }[] = [
  { key: "SHIFT", label: "Theo ca" },
  { key: "OFFICE", label: "Hành chính" },
];

/**
 * Kỹ thuật viên tự chọn hình thức làm việc của mình: Theo ca (đăng ký từng ca) hoặc Hành chính
 * (tự trực mọi ca T2–T6, trừ ngày lễ). Chuyển sang Hành chính thì backend bỏ các ca ngày thường đã đăng ký.
 */
export const WorkModeSwitch = () => {
  const { can } = usePermissions();
  const { actor } = useActor();
  const canHandle = can(PERMISSIONS.taskHandle);
  const workModes = useWorkModes(canHandle);
  const setMode = useSetWorkMode();
  const mine = workModes.data?.find((item) => item.subject === actor?.subject);

  // Chỉ người xử lý việc hiện tại của workspace mới có hình thức làm việc
  if (!canHandle || !mine) return null;

  const handleChange = (mode: WorkModeKind) => {
    if (mode === mine.mode || setMode.isPending) return;
    setMode.mutate(
      { subject: "me", mode },
      {
        onSuccess: () =>
          toast.success(
            mode === "OFFICE" ? "Đã chuyển sang Hành chính" : "Đã chuyển sang Theo ca",
            {
              description:
                mode === "OFFICE"
                  ? "Bạn tự trực các ca T2–T6, không cần đăng ký."
                  : "Hãy đăng ký các ca bạn trực trong lịch bên dưới.",
            },
          ),
        onError: (error) => toastError("Không đổi được hình thức làm việc.", error),
      },
    );
  };

  return (
    <div className="flex items-center gap-2">
      <span className="text-crm-sm text-crm-text-2">Hình thức</span>
      <ViewTabs
        label="Hình thức làm việc"
        tabs={MODE_TABS}
        value={mine.mode}
        onChange={handleChange}
      />
    </div>
  );
};
