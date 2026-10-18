import type {JsonRequest} from '../../../platform/auth/json-client.ts';
import type {Page, ProcessHandle} from '../../../platform/process/contracts.ts';
export interface NewTicket { readonly clientRequestId: string; readonly title: string; readonly description: string }
export interface Ticket extends NewTicket {
  readonly id: string;
  readonly workspace: string;
  readonly creator: string;
  readonly createdAt: string;
  readonly process: ProcessHandle;
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
export function createTicketClient(request: JsonRequest) {
  const base = (workspace: string) => `/api/apps/servicedesk/v1/workspaces/${encodeURIComponent(workspace)}/tickets`;
  return Object.freeze({
    list: (workspace: string, query = '') => request<Page<Ticket>>(`${base(workspace)}?q=${encodeURIComponent(query)}`),
    get: (workspace: string, id: string) => request<Ticket>(`${base(workspace)}/${encodeURIComponent(id)}`),
    create: (workspace: string, input: NewTicket) => request<Ticket>(base(workspace), {method: 'POST', body: JSON.stringify(input)}),
    statistics: (workspace: string) => request<TicketStatistics>(`${base(workspace)}/statistics`),
  });
}
