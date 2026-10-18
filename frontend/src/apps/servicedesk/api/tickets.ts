import type {JsonRequest} from '../../../platform/auth/json-client.ts';
import type {Page} from '../../../platform/process/contracts.ts';
/** Service Desk owns its wire projections; identifiers remain opaque. */
export interface TicketProcessView {
  readonly id?: string;
  readonly processInstanceId: string;
  readonly processKey: string;
  readonly businessKey: string;
  readonly workspace: string;
}
export interface NewTicket { readonly clientRequestId: string; readonly title: string; readonly description: string }
export interface Ticket extends NewTicket {
  readonly id: string;
  readonly workspace: string;
  readonly creator: string;
  readonly createdAt: string;
  readonly process: TicketProcessView;
}
export interface TicketStatistics {
  readonly workspace: string;
  readonly visibility: 'own' | 'workspace';
  readonly total: number;
  readonly awaitingHandling: number;
  readonly awaitingConfirmation: number;
  readonly completed: number;
  readonly unclassified: number;
}
export interface TicketFilter { readonly q?: string; readonly from?: string; readonly to?: string }
export interface TicketSearch extends TicketFilter { readonly page?: number; readonly size?: number }
export interface TicketTask {
  readonly id: string;
  readonly processInstanceId: string;
  readonly key: string;
  readonly name: string;
  readonly candidateGroups: readonly string[];
  readonly assignee: string | null;
  readonly createdAt: string;
}
export interface TicketStep {
  readonly id: string;
  readonly key: string;
  readonly name: string;
  readonly assignee: string | null;
  readonly actor: string | null;
  readonly outcome: string | null;
  readonly comment: string | null;
  readonly startedAt: string;
  readonly claimedAt: string | null;
  readonly endedAt: string | null;
}
export interface TicketProgress {
  readonly id: string;
  readonly processKey: string;
  readonly businessKey: string;
  readonly workspace: string;
  readonly initiator: string;
  readonly startedAt: string;
  readonly endedAt: string | null;
  readonly ended: boolean;
  readonly currentTasks: readonly TicketTask[];
  readonly steps: readonly TicketStep[];
  readonly tasks: readonly TicketStep[];
}
export interface TicketTriage {
  readonly contractVersion: '0.1.0';
  readonly requestId: string;
  readonly profile: 'service-desk';
  readonly workspaceId: string;
  readonly status: 'SUCCESS' | 'DEGRADED';
  readonly needsReview: boolean;
  readonly result: {readonly category: string; readonly priority: string; readonly suggestedGroup: string} | null;
  readonly evidence: readonly {readonly type: 'INPUT'; readonly field: 'title' | 'description'; readonly text: string}[];
  readonly explanation: {
    readonly matchedRules: readonly string[];
    readonly impact: string | null;
    readonly urgency: string | null;
    readonly reasonCode: 'CLASSIFIED' | 'ROOT_CAUSE_CONFLICT' | 'INSUFFICIENT_INFORMATION';
    readonly text: string;
  } | null;
  readonly runtime: {
    readonly engine: string;
    readonly profileVersion: 'service-desk@0.2.0';
    readonly rulesVersion?: string;
    readonly modelVersion?: string;
  } | null;
  readonly error: {readonly code: 'INTELLIGENCE_UNAVAILABLE'} | null;
}

function query(filters: TicketSearch): string {
  const parameters = new URLSearchParams();
  for (const [key, value] of Object.entries(filters)) {
    if (value !== undefined) parameters.set(key, String(value));
  }
  return parameters.size ? `?${parameters}` : '';
}
export function createTicketClient(request: JsonRequest) {
  const base = (workspace: string) => `/api/apps/servicedesk/v1/workspaces/${encodeURIComponent(workspace)}/tickets`;
  return Object.freeze({
    list: (workspace: string, filters: TicketSearch | string = {}) => request<Page<Ticket>>(`${base(workspace)}${query(typeof filters === 'string' ? {q: filters} : filters)}`),
    get: (workspace: string, id: string) => request<Ticket>(`${base(workspace)}/${encodeURIComponent(id)}`),
    create: (workspace: string, input: NewTicket) => request<Ticket>(base(workspace), {method: 'POST', body: JSON.stringify(input)}),
    statistics: (workspace: string, filters: TicketFilter = {}) => request<TicketStatistics>(`${base(workspace)}/statistics${query(filters)}`),
    progress: (workspace: string, id: string) => request<TicketProgress>(`${base(workspace)}/${encodeURIComponent(id)}/progress`),
    triage: (workspace: string, id: string) => request<TicketTriage>(`${base(workspace)}/${encodeURIComponent(id)}/triage`, {method: 'POST'}),
  });
}
