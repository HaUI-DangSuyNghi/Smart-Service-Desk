import type { ComponentType, ReactNode } from "react";
import type { WorkItem } from "@/platform/services";

export interface InboxActionProps {
  task: WorkItem;
  fallback: ReactNode;
}

export type InboxActionResolver = (processKey: string) => ComponentType<InboxActionProps> | null;
let resolver: InboxActionResolver = () => null;
export const configureInboxActions = (value: InboxActionResolver) => {
  resolver = value;
};
export const inboxAction: InboxActionResolver = (processKey) => resolver(processKey);
