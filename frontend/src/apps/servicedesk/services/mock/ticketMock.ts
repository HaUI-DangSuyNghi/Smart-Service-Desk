import type { Page } from "../../../../platform/shared/api";
import {
  mockError,
  workMock,
  type MockProcess,
  type MockUser,
  type NextTaskSpec,
  type Priority,
} from "../../../../platform/services";
import {
  DEFAULT_CATEGORY_GROUPS,
  DEFAULT_ESTIMATES,
  MAX_DESCRIPTION,
  MAX_TITLE,
  type Category,
} from "../../tickets/ticketRules";
import type {
  Assessment,
  Classification,
  ClassificationCorrection,
  Correction,
  NewTicket,
  ReviewDecision,
  ServiceDeskSettings,
  Ticket,
  TicketQuery,
  TicketState,
  TicketStatistics,
  TicketView,
} from "../ticketService";
import { assessText } from "./mockAssessment";
import { buildSeeds, type SeedTicket } from "./ticketSeeds";

// Kho ticket giả theo contracts/openapi/servicedesk-target.yaml; trạng thái lấy từ bộ máy giả của Tầng 2

const PROCESS_KEY = "service-desk";
// AI chạy nền vài giây sau khi tạo ticket (plan mục 2)
const ASSESSMENT_DELAY_MS = 4000;
// Ticket mẫu có AI chưa xong: để người xem thấy trạng thái "đang phân tích" trước khi có kết quả
const PENDING_SEED_MS = 45 * 1000;

interface MockTicket {
  ticket: Ticket;
  classification: Classification | null;
  assessment: Assessment;
  /** Trước mốc này assessment hiện PENDING. */
  assessmentReadyAt: number;
  corrections: Correction[];
  closedBy: "REQUESTER" | "AUTO" | null;
  /** Phút đã làm của các lần xử lý trước; mở lại thì cộng tiếp. */
  workedMinutes: number;
}

const tickets = new Map<string, MockTicket>();
let settings: ServiceDeskSettings = {
  version: 1,
  updatedAt: new Date().toISOString(),
  updatedBy: "mock-admin",
  categoryGroups: { ...DEFAULT_CATEGORY_GROUPS },
  estimateDefaults: structuredClone(DEFAULT_ESTIMATES),
};

const nowIso = () => new Date().toISOString();
const minutesAgo = (minutes: number) => new Date(Date.now() - minutes * 60 * 1000).toISOString();

const groupOf = (category: Category) => settings.categoryGroups[category] ?? "general";
const estimateOf = (category: Category, priority: Priority) =>
  settings.estimateDefaults[category][priority];

// ---------- Các bước của quy trình service-desk ----------

const reviewStep = (workspace: string): NextTaskSpec => ({
  key: "review",
  name: "Duyệt và giao việc",
  candidateGroups: [`${workspace}:coordinator`],
});

const handleStep = (
  workspace: string,
  classification: Classification,
  assignee: string | null,
  estimateMinutes: number,
  slaFrom: string,
): NextTaskSpec => ({
  key: "handle",
  name: "Xử lý yêu cầu",
  candidateGroups: [`${workspace}:agent`],
  assignee,
  queued: true,
  group: classification.group,
  priority: classification.priority,
  basePriority: classification.basePriority,
  escalated: classification.escalated,
  estimate: {
    minutes: estimateMinutes,
    basis:
      estimateMinutes === estimateOf(classification.category, classification.priority)
        ? "DEFAULT"
        : "COORDINATOR",
  },
  slaFrom,
});

const confirmStep = (initiator: string): NextTaskSpec => ({
  key: "confirm",
  name: "Xác nhận kết quả",
  candidateGroups: [],
  assignee: initiator,
});

const lastHandler = (process: MockProcess) =>
  [...process.steps].reverse().find((step) => step.key === "handle")?.assignee ?? null;

workMock.registerFlow(PROCESS_KEY, {
  next: (process, task, outcome) => {
    const record = tickets.get(process.businessKey);
    if (task.key === "handle") {
      if (record) record.workedMinutes = task.workedBefore;
      return confirmStep(process.initiator);
    }
    if (task.key === "confirm" && outcome === "rework" && record?.classification) {
      // R11: quay về đúng người đã xử lý, cộng tiếp thời gian đã làm
      workMock.pushAlert(process.workspace, process, "REOPENED", "WARNING");
      return {
        ...handleStep(
          process.workspace,
          record.classification,
          lastHandler(process),
          estimateOf(record.classification.category, record.classification.priority),
          record.ticket.createdAt,
        ),
        // Bước xác nhận vừa ghi vào lịch sử nên đã tính cả lần này
        reopenCount: process.steps.filter((step) => step.outcome === "rework").length,
        workedBefore: record.workedMinutes,
      };
    }
    if (task.key === "confirm" && record) record.closedBy = "REQUESTER";
    return null;
  },
  // Trả việc: về hàng chờ duyệt để điều phối viên giao lại
  release: (process) => reviewStep(process.workspace),
});

// ---------- Ghép view ----------

const stateOf = (process: MockProcess): TicketState => {
  const task = process.task;
  if (process.endedAt) return "CLOSED";
  if (!task) return "UNCLASSIFIED";
  if (task.key === "review") return "AWAITING_REVIEW";
  if (task.key === "confirm") return "AWAITING_CONFIRMATION";
  if (task.workState === "IN_PROGRESS") return "IN_PROGRESS";
  if (task.workState === "PENDING") return "PENDING";
  return task.reopenCount > 0 && task.firstStartedAt === null ? "REOPENED" : "ASSIGNED";
};

const pendingAssessment = (): Assessment => ({
  status: "PENDING",
  needsReview: true,
  nextQuestions: [],
});

const toView = (record: MockTicket, withCorrections: boolean): TicketView => {
  const process = workMock.findProcess(record.ticket.process.processInstanceId);
  if (!process) return record.ticket;
  const work = workMock.queueItemOf(process);
  // ET, SLA, thời gian làm chỉ có ở bước xử lý
  const handling = work?.key === "handle" ? work : null;
  return {
    ...record.ticket,
    state: stateOf(process),
    classification: record.classification
      ? {
          ...record.classification,
          priority: handling?.priority ?? record.classification.priority,
          escalated: handling?.escalated ?? record.classification.escalated,
        }
      : null,
    assessment: Date.now() < record.assessmentReadyAt ? pendingAssessment() : record.assessment,
    assignee: work?.assignee ?? null,
    taskId: work?.id ?? null,
    pendingReason: handling?.pendingReason ?? null,
    estimate: handling?.estimate ?? null,
    workedMinutes: handling?.workedMinutes ?? record.workedMinutes,
    sla: handling?.sla ?? null,
    expectedStartAt: handling?.expectedStartAt ?? null,
    reopenCount: handling?.reopenCount ?? 0,
    closedAt: process.endedAt,
    closedBy: process.endedAt ? (record.closedBy ?? "REQUESTER") : null,
    corrections: withCorrections ? record.corrections : undefined,
  };
};

const findRecord = (workspace: string, id: string) => {
  const record = tickets.get(id);
  if (!record || record.ticket.workspace !== workspace) {
    throw mockError(404, "Không tìm thấy ticket, hoặc bạn không có quyền xem.");
  }
  return record;
};

const processOf = (record: MockTicket) => {
  const process = workMock.findProcess(record.ticket.process.processInstanceId);
  if (!process) throw mockError(404, "Không tìm thấy quy trình của ticket.");
  return process;
};

// ---------- Tạo ticket, dữ liệu mẫu ----------

const addTicket = (workspace: string, seed: SeedTicket): MockTicket => {
  const id = seed.id;
  const createdAt = minutesAgo(seed.createdMinutesAgo);
  const assessment = seed.assessment ?? assessText(seed.title, seed.description);
  const classification: Classification | null = seed.handle
    ? {
        category: seed.handle.category,
        impact: assessment.impact ?? null,
        urgency: assessment.urgency ?? null,
        priority: seed.handle.priority,
        basePriority: seed.handle.priority,
        escalated: false,
        group: groupOf(seed.handle.category),
        source: assessment.category === seed.handle.category ? "AI" : "COORDINATOR",
        reviewedBy: "mock-coordinator",
        reviewedAt: createdAt,
      }
    : null;

  let task: NextTaskSpec | null = reviewStep(workspace);
  if (classification && seed.handle) {
    task = {
      ...handleStep(
        workspace,
        classification,
        seed.handle.assignee,
        estimateOf(classification.category, classification.priority),
        createdAt,
      ),
      createdAt,
      ...seed.handle.task,
    };
  }
  if (seed.stage === "confirm")
    task = { ...confirmStep(seed.creator), createdAt: minutesAgo(seed.confirmMinutesAgo ?? 30) };
  if (seed.stage === "closed") task = null;

  const process = workMock.createProcess({
    workspace,
    processKey: PROCESS_KEY,
    businessKey: id,
    label: seed.title,
    initiator: seed.creator,
    startedAt: createdAt,
    steps:
      seed.stage && seed.handle
        ? [
            {
              id: crypto.randomUUID(),
              key: "handle",
              name: "Xử lý yêu cầu",
              assignee: seed.handle.assignee,
              outcome: "resolved",
              comment: "Đã thay dây nguồn, máy chạy bình thường.",
              startedAt: createdAt,
              claimedAt: createdAt,
              endedAt: minutesAgo(seed.closedMinutesAgo ?? 30),
            },
          ]
        : [],
    task,
  });
  // Đóng từ lâu: createProcess đóng quy trình ở thời điểm hiện tại
  if (seed.stage === "closed") process.endedAt = minutesAgo(seed.closedMinutesAgo ?? 30);

  const record: MockTicket = {
    ticket: {
      id,
      workspace,
      creator: seed.creator,
      clientRequestId: crypto.randomUUID(),
      title: seed.title,
      description: seed.description,
      createdAt,
      process: {
        processInstanceId: process.processInstanceId,
        processKey: PROCESS_KEY,
        businessKey: id,
        workspace,
      },
    },
    classification,
    assessment,
    assessmentReadyAt: seed.assessmentPending ? Date.now() + PENDING_SEED_MS : 0,
    corrections: [],
    closedBy: seed.stage === "closed" ? (seed.closedBy ?? "REQUESTER") : null,
    workedMinutes: seed.stage ? 40 : 0,
  };
  tickets.set(id, record);
  return record;
};

/** Nạp bộ ticket cố định ở ticketSeeds.ts; phủ đủ cột của Board và các trường hợp của plan. */
const seedWorkspace = (workspace: string, user: MockUser) => {
  buildSeeds(user.sub, minutesAgo).forEach((seed) => addTicket(workspace, seed));

  // Cảnh báo mẫu để thấy chuông có số
  workMock
    .listProcesses(workspace, PROCESS_KEY)
    .filter((process) => process.task?.priority === "LOW" && process.task.queued)
    .forEach((process) => workMock.pushAlert(workspace, process, "START_OVERDUE", "WARNING"));
};

workMock.registerSeed(seedWorkspace);

// ---------- API giả (cùng chữ ký với ticketService) ----------

const hasTicket = (id: string) => tickets.has(id);

const matches = (view: TicketView, query: TicketQuery, user: MockUser) => {
  const text = query.q?.trim().toLowerCase();
  if (text && !`${view.title} ${view.description}`.toLowerCase().includes(text)) return false;
  if (query.state?.length && (!view.state || !query.state.includes(view.state))) return false;
  const priority = view.classification?.priority;
  if (query.priority?.length && (!priority || !query.priority.includes(priority))) return false;
  if (query.category && view.classification?.category !== query.category) return false;
  const assignee = query.assignee === "me" ? user.sub : query.assignee;
  if (assignee && view.assignee !== assignee) return false;
  if (query.overdue && !(view.sla?.startBreached || view.sla?.resolveBreached)) return false;
  return true;
};

const PRIORITY_RANK: Record<Priority, number> = { CRITICAL: 4, HIGH: 3, MEDIUM: 2, LOW: 1 };

const listTickets = async (workspace: string, query: TicketQuery): Promise<Page<TicketView>> => {
  const user = await workMock.prepare(workspace);
  const views = [...tickets.values()]
    .filter((record) => record.ticket.workspace === workspace)
    .map((record) => toView(record, false))
    .filter((view) => matches(view, query, user));

  views.sort((a, b) => {
    if (query.sort === "priority") {
      const priority =
        PRIORITY_RANK[b.classification?.priority ?? b.assessment?.priority ?? "LOW"] -
        PRIORITY_RANK[a.classification?.priority ?? a.assessment?.priority ?? "LOW"];
      if (priority !== 0) return priority;
      return a.createdAt.localeCompare(b.createdAt);
    }
    return b.createdAt.localeCompare(a.createdAt);
  });

  const start = query.page * query.size;
  return {
    items: views.slice(start, start + query.size),
    page: query.page,
    size: query.size,
    total: views.length,
  };
};

const getTicket = async (workspace: string, id: string) => {
  await workMock.prepare(workspace);
  return toView(findRecord(workspace, id), true);
};

const createTicket = async (workspace: string, input: NewTicket) => {
  const user = await workMock.prepare(workspace);
  const title = input.title.trim();
  const description = input.description.trim();
  if (!title || title.length > MAX_TITLE || !description || description.length > MAX_DESCRIPTION) {
    throw mockError(400, "Tiêu đề hoặc mô tả không hợp lệ.");
  }
  const duplicate = [...tickets.values()].some(
    ({ ticket }) =>
      ticket.workspace === workspace &&
      ticket.creator === user.sub &&
      ticket.clientRequestId === input.clientRequestId,
  );
  if (duplicate) throw mockError(409, "Yêu cầu này đã được gửi.");

  const record = addTicket(workspace, {
    id: crypto.randomUUID(),
    title,
    description,
    creator: user.sub,
    createdMinutesAgo: 0,
  });
  record.ticket.clientRequestId = input.clientRequestId;
  record.assessmentReadyAt = Date.now() + ASSESSMENT_DELAY_MS;
  const process = processOf(record);
  if (record.assessment.priority === "CRITICAL") {
    workMock.pushAlert(workspace, process, "CRITICAL_NEW", "CRITICAL");
  }
  return toView(record, false);
};

const correctionsOf = (
  record: MockTicket,
  values: { category: Category; priority: Priority },
  user: MockUser,
  reason: string,
) => {
  // So với giá trị đang hiệu lực, lần đầu là gợi ý AI
  const previous = record.classification ?? {
    category: record.assessment.category ?? null,
    priority: record.assessment.priority ?? null,
  };
  const fromAi = record.classification === null;
  const changes: Correction[] = [];
  (["category", "priority"] as const).forEach((field) => {
    if (previous[field] !== values[field]) {
      changes.push({
        field,
        previousValue: previous[field] ?? null,
        newValue: values[field],
        fromAi,
        by: user.sub,
        reason,
        at: nowIso(),
      });
    }
  });
  return changes;
};

const reviewTicket = async (workspace: string, id: string, decision: ReviewDecision) => {
  const user = await workMock.prepare(workspace);
  const record = findRecord(workspace, id);
  const process = processOf(record);
  if (process.task?.key !== "review") throw mockError(409, "Ticket đã được người khác duyệt.");
  if (decision.assignee === record.ticket.creator) {
    throw mockError(403, "Không giao cho người tạo ticket.");
  }
  const reason = decision.reason?.trim() ?? "";
  const changes = correctionsOf(record, decision, user, reason);
  const defaultEstimate = estimateOf(decision.category, decision.priority);
  if (decision.estimateMinutes !== defaultEstimate) {
    changes.push({
      field: "estimateMinutes",
      previousValue: String(defaultEstimate),
      newValue: String(decision.estimateMinutes),
      by: user.sub,
      reason,
      at: nowIso(),
    });
  }
  if (changes.some((change) => change.fromAi) && !reason) {
    throw mockError(400, "Cần ghi lý do khi khác gợi ý của AI.");
  }

  record.corrections.push(...changes);
  record.classification = {
    category: decision.category,
    impact: decision.impact ?? null,
    urgency: decision.urgency ?? null,
    priority: decision.priority,
    basePriority: decision.priority,
    escalated: false,
    group: groupOf(decision.category),
    source: changes.some((change) => change.fromAi) ? "COORDINATOR" : "AI",
    reviewedBy: user.sub,
    reviewedAt: nowIso(),
  };
  workMock.advance(
    process,
    { outcome: "approved", comment: reason || null, actor: user.sub },
    handleStep(
      workspace,
      record.classification,
      decision.assignee,
      decision.estimateMinutes,
      record.ticket.createdAt,
    ),
  );
  if (decision.priority === "CRITICAL") {
    workMock.pushAlert(workspace, process, "CRITICAL_NEW", "CRITICAL");
  }
  return toView(record, true);
};

const correctClassification = async (
  workspace: string,
  id: string,
  correction: ClassificationCorrection,
) => {
  const user = await workMock.prepare(workspace);
  const record = findRecord(workspace, id);
  const process = processOf(record);
  const task = process.task;
  if (!record.classification || task?.key !== "handle") {
    throw mockError(409, "Chỉ sửa được phân loại khi ticket đang xử lý.");
  }
  const next = {
    category: correction.category ?? record.classification.category,
    priority: correction.priority ?? task.priority,
  };
  const changes = correctionsOf(
    record,
    { category: next.category, priority: next.priority },
    user,
    correction.reason.trim(),
  );
  record.corrections.push(...changes);
  const categoryChanged = next.category !== record.classification.category;
  record.classification = {
    ...record.classification,
    category: next.category,
    priority: next.priority,
    basePriority: next.priority,
    escalated: false,
    group: groupOf(next.category),
    source: "AGENT",
  };
  task.priority = next.priority;
  task.basePriority = next.priority;
  task.escalated = false;

  // R9: đổi loại khi chưa bắt đầu thì đổi tổ → quay về chờ duyệt để đề xuất người lại
  if (categoryChanged && task.firstStartedAt === null) {
    record.classification = null;
    workMock.advance(
      process,
      { outcome: "reclassified", comment: correction.reason.trim(), actor: user.sub },
      reviewStep(workspace),
    );
  }
  return toView(record, true);
};

const getStatistics = async (workspace: string): Promise<TicketStatistics> => {
  await workMock.prepare(workspace);
  const views = [...tickets.values()]
    .filter((record) => record.ticket.workspace === workspace)
    .map((record) => toView(record, false));
  const count = (...states: TicketState[]) =>
    views.filter((view) => view.state && states.includes(view.state)).length;
  return {
    workspace,
    visibility: "workspace",
    total: views.length,
    awaitingHandling: count("ASSIGNED", "IN_PROGRESS", "PENDING", "REOPENED"),
    awaitingConfirmation: count("AWAITING_CONFIRMATION"),
    completed: count("CLOSED"),
    unclassified: count("UNCLASSIFIED"),
    awaitingReview: count("AWAITING_REVIEW"),
    assigned: count("ASSIGNED"),
    inProgress: count("IN_PROGRESS"),
    pending: count("PENDING"),
    reopened: count("REOPENED"),
    closed: count("CLOSED"),
    overdue: views.filter((view) => view.sla?.startBreached || view.sla?.resolveBreached).length,
  };
};

const getSettings = async () => settings;

const updateSettings = async (next: ServiceDeskSettings) => {
  const user = await workMock.currentUser();
  if (next.version !== settings.version) throw mockError(409, "Người khác vừa sửa cấu hình.");
  settings = { ...next, version: next.version + 1, updatedAt: nowIso(), updatedBy: user.sub };
  return settings;
};

/** Xóa kho ticket (test). */
const resetTicketMock = () => tickets.clear();

export const ticketMock = {
  hasTicket,
  listTickets,
  getTicket,
  createTicket,
  reviewTicket,
  correctClassification,
  getStatistics,
  getSettings,
  updateSettings,
  resetTicketMock,
};
