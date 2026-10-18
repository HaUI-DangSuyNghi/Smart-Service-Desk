export interface Page<T> { readonly items: readonly T[]; readonly page: number; readonly size: number; readonly total: number }
export interface ProcessHandle {
  readonly id?: string;
  readonly processInstanceId: string;
  readonly processKey: string;
  readonly businessKey: string;
  readonly workspace: string;
}
export interface WorkItem {
  readonly id: string;
  readonly processInstanceId: string;
  readonly processKey: string;
  readonly businessKey: string;
  readonly key: string;
  readonly name: string;
  readonly candidateGroups: readonly string[];
  readonly assignee: string | null;
  readonly createdAt: string;
}
export interface ProcessStep {
  readonly id: string;
  readonly key: string;
  readonly name: string;
  readonly assignee: string | null;
  readonly outcome: string | null;
  readonly comment: string | null;
  readonly startedAt: string;
  readonly claimedAt: string | null;
  readonly endedAt: string | null;
}
export interface ProcessTimeline {
  readonly id: string;
  readonly processKey: string;
  readonly businessKey: string;
  readonly workspace: string;
  readonly initiator: string;
  readonly startedAt: string;
  readonly endedAt: string | null;
  readonly currentTasks: readonly WorkItem[];
  readonly steps: readonly ProcessStep[];
}
export interface ProcessCompletion { readonly expectedTaskKey: string; readonly outcome: string; readonly comment?: string }
export type InboxScope = 'CANDIDATE' | 'ASSIGNED';
