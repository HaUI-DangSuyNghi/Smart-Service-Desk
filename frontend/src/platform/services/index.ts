// Mọi API của Tầng 2 (core, hpdi). API riêng của app nằm ở apps/<app>/services.
export { getActor, type Actor } from "./actorService";
export { getMyActivity, type ActivityEntry } from "./activityService";
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
  claimTask,
  completeTask,
  getTimeline,
  listInbox,
  listMyRequests,
  listProcesses,
  startProcess,
  type InboxScope,
  type PageQuery,
  type ProcessDescriptor,
  type ProcessHandle,
  type ProcessOption,
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
  getMyWorkspaces,
  type WorkspaceMember,
  type WorkspacePermission,
  type WorkspaceSummary,
} from "./workspaceService";
