// Khối P (Process): công việc, tiến độ, tạo yêu cầu – dùng chung cho mọi processKey
export {
  configureInboxActions,
  type InboxActionProps,
  type InboxActionResolver,
} from "./common/inboxActions";
export {
  configureProcessLinks,
  processLink,
  type ProcessLinkResolver,
} from "./common/processLinks";
export {
  configureRequestActions,
  type RequestActionProps,
  type RequestActionResolver,
} from "./common/requestActions";
export { type ProcessLabels } from "./catalog/processLabels";
export { useProcessLabels } from "./catalog/useProcessCatalog";
/** Danh sách công việc và yêu cầu cá nhân dùng trên trang chủ. */
export { useInbox } from "./inbox/useInbox";
export { useRequests } from "./requests/useMyRequests";
export { RequestActions } from "./requests/RequestActions";
export { StartProcessDialog } from "./start/StartProcessDialog";
export { outcomeTone } from "./common/display";
export { InboxBadge } from "./inbox/InboxBadge";
export { default as InboxPage } from "./inbox/InboxPage";
export { default as MyRequestsPage } from "./requests/MyRequestsPage";
export { ProcessTimeline } from "./timeline/ProcessTimeline";
export { default as ProcessTimelinePage } from "./timeline/ProcessTimelinePage";
export { useTimeline } from "./timeline/useTimeline";
export { useProcessRealtime } from "./common/useProcessRealtime";
export { AssignTaskDialog } from "./tasks/AssignTaskDialog";
export { CompleteTaskDialog } from "./tasks/CompleteTaskDialog";
// Hàng đợi, trạng thái Đang làm/Đang chờ, hạn xử lý, ước tính, cảnh báo, cấu hình chung (plan nghiệp vụ R2–R8)
export { AlertBadge } from "./alerts/AlertBadge";
export { default as AlertsPage } from "./alerts/AlertsPage";
export { AlertWatcher } from "./alerts/AlertWatcher";
export { WorkSettingsSection } from "./settings/WorkSettingsSection";
export { AssigneeMenu } from "./work/AssigneeMenu";
export { AssignmentSuggestionPanel } from "./work/AssignmentSuggestionPanel";
export { EstimateText } from "./work/EstimateText";
export { default as EstimateRequestsPage } from "./work/EstimateRequestsPage";
export { PriorityTag } from "./work/PriorityTag";
export { PauseDialog } from "./work/PauseDialog";
export { ReleaseDialog } from "./work/ReleaseDialog";
export { useQueue, useResumeWork, useStartWork } from "./work/useWork";
export { workErrorMessage } from "./work/workErrors";
export { SlaIndicator } from "./work/SlaIndicator";
export { WorkActions } from "./work/WorkActions";
export { WorkStateTag } from "./work/WorkStateTag";
export {
  formatMinutes,
  PENDING_REASON_LABELS,
  PRIORITY_LABELS,
  WAIT_FOR_SHIFT,
} from "./work/workLabels";
