import { z } from "zod";
import { apiClient } from "../shared/api";
import { hpdiWorkspace } from "./paths";

const field = z.object({
  key: z.string(),
  label: z.string(),
  value: z.string(),
  maxLength: z.number().int().positive(),
  multiline: z.boolean(),
});
export const requestManagementSchema = z.object({
  processId: z.string(),
  version: z.string().regex(/^[a-f0-9]{64}$/),
  editable: z.boolean(),
  cancellable: z.boolean(),
  closable: z.boolean().default(false),
  cancelled: z.boolean(),
  explanation: z.string(),
  fields: z.array(field),
  history: z.array(
    z.object({
      id: z.string().uuid(),
      version: z.number(),
      action: z.enum(["UPDATED", "CANCELLED", "CLOSED"]),
      actor: z.string(),
      reason: z.string(),
      beforeValues: z.record(z.string()),
      afterValues: z.record(z.string()),
      createdAt: z.string(),
    }),
  ),
});
export type RequestManagement = z.infer<typeof requestManagementSchema>;
const endpoint = (workspace: string, id: string) =>
  `${hpdiWorkspace(workspace)}/instances/${encodeURIComponent(id)}/request`;
export const readRequestManagement = async (
  workspace: string,
  id: string,
  signal?: AbortSignal,
) => {
  const { data } = await apiClient.get<unknown>(endpoint(workspace, id), { signal });
  return requestManagementSchema.parse(data);
};
export const updateRequest = async (
  workspace: string,
  id: string,
  expectedVersion: string,
  values: Record<string, string>,
  reason: string,
) => {
  const { data } = await apiClient.patch<unknown>(endpoint(workspace, id), {
    expectedVersion,
    values,
    reason,
  });
  return requestManagementSchema.parse(data);
};
export const cancelRequest = async (
  workspace: string,
  id: string,
  expectedVersion: string,
  reason: string,
) => {
  const { data } = await apiClient.post<unknown>(`${endpoint(workspace, id)}/cancel`, {
    expectedVersion,
    reason,
  });
  return requestManagementSchema.parse(data);
};

export const closeRequest = async (
  workspace: string,
  id: string,
  expectedVersion: string,
  reason: string,
) => {
  const { data } = await apiClient.post<unknown>(`${endpoint(workspace, id)}/close`, {
    expectedVersion,
    reason,
  });
  return requestManagementSchema.parse(data);
};
