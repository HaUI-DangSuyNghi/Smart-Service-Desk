import type { ComponentType, ReactNode } from "react";
import type { WorkItem } from "@/platform/services";

export interface InboxActionProps {
  task: WorkItem;
  fallback: ReactNode;
}

export type InboxActionResolver = (processKey: string) => ComponentType<InboxActionProps> | null;
export type InboxLabelLoader = (
  workspace: string,
  task: WorkItem,
  signal?: AbortSignal,
) => Promise<string>;
export type InboxLabelResolver = (processKey: string) => InboxLabelLoader | null;
let resolver: InboxActionResolver = () => null;
let labelResolver: InboxLabelResolver = () => null;
export const configureInboxActions = (
  value: InboxActionResolver,
  labels: InboxLabelResolver = () => null,
) => {
  resolver = value;
  labelResolver = labels;
};
export const inboxAction: InboxActionResolver = (processKey) => resolver(processKey);
export const inboxLabel: InboxLabelResolver = (processKey) => labelResolver(processKey);
