// Mọi API của Tầng 2 (core, hpdi). API riêng của app nằm ở apps/<app>/services.
export { getActor, type Actor } from "./actorService";
export {
  getMyActivity,
  getWorkspaceAudit,
  type ActivityEntry,
  type AuditFilter,
} from "./activityService";
export {
  createKnowledge,
  describeFile,
  downloadFile,
  getKnowledgeContent,
  getKnowledgeRecord,
  searchKnowledge,
  type KnowledgeContent,
  type KnowledgeQuery,
  type KnowledgeRecord,
  type KnowledgeSummary,
  type NewKnowledge,
  type StoredFile,
} from "./knowledgeService";
export { getMe, type Me, type Membership } from "./meService";
export {
  assignTask,
  claimTask,
  completeTask,
  getProcessStatistics,
  getTimeline,
  listInbox,
  listProcesses,
  listRequests,
  startProcess,
  type InboxScope,
  type PageQuery,
  type ProcessCount,
  type ProcessStatistics,
  type RequestScope,
  type StatisticsQuery,
  type ProcessDescriptor,
  type ProcessHandle,
  type ProcessOption,
  type ProcessStartField,
  type ProcessStep,
  type ProcessSummary,
  type ProcessTaskDescriptor,
  type ProcessTimeline,
  type TaskCompletion,
  type WorkItem,
} from "./processService";
export {
  getMembers,
  getMyPermissions,
  getPeopleNames,
  getMyWorkspaces,
  type WorkspaceMember,
  type WorkspacePermission,
  type WorkspaceSummary,
} from "./workspaceService";
