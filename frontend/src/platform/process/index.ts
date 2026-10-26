// Khối P (Process): công việc, tiến độ, tạo yêu cầu – dùng chung cho mọi processKey
export { configureProcessLinks, type ProcessLinkResolver } from "./common/processLinks";
export { type ProcessLabels } from "./catalog/processLabels";
export { useProcessLabels } from "./catalog/useProcessCatalog";
export { outcomeTone } from "./common/display";
export { InboxBadge } from "./inbox/InboxBadge";
export { default as InboxPage } from "./inbox/InboxPage";
export { default as MyRequestsPage } from "./requests/MyRequestsPage";
export { ProcessTimeline } from "./timeline/ProcessTimeline";
export { default as ProcessTimelinePage } from "./timeline/ProcessTimelinePage";
export { useTimeline } from "./timeline/useTimeline";
