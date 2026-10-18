import type {JsonRequest} from '../auth/json-client.ts';
import type {Page, WorkItem, ProcessTimeline, ProcessCompletion, InboxScope} from './contracts.ts';

export function createProcessClient(request: JsonRequest) {
  const base = (workspace: string) => `/api/hpdi/v1/workspaces/${encodeURIComponent(workspace)}`;
  return Object.freeze({
    inbox: (workspace: string, scope: InboxScope = 'CANDIDATE') => request<Page<WorkItem>>(`${base(workspace)}/inbox?scope=${encodeURIComponent(scope)}`),
    timeline: (workspace: string, id: string) => request<ProcessTimeline>(`${base(workspace)}/instances/${encodeURIComponent(id)}/timeline`),
    claim: (workspace: string, id: string) => request<WorkItem>(`${base(workspace)}/tasks/${encodeURIComponent(id)}/claim`, {method: 'POST'}),
    complete: (workspace: string, id: string, completion: ProcessCompletion) => request<void>(`${base(workspace)}/tasks/${encodeURIComponent(id)}/complete`, {
      method: 'POST', body: JSON.stringify(completion),
    }),
  });
}
