import { z } from "zod";
import { apiClient, ApiError } from "../../../platform/shared/api";

const schema = z
  .object({
    contractVersion: z.literal("intake-admission@1.0.0"),
    ticketId: z.string().uuid(),
    contextRevision: z.number().int().nonnegative(),
    version: z.number().int().nonnegative(),
    reviewRound: z.number().int().positive(),
    status: z.enum([
      "NEW",
      "NEEDS_ENRICHMENT",
      "DUPLICATE_HOLD",
      "FINAL_ASSESSMENT_QUEUED",
      "ASSESSING",
      "REVIEW_RELEASED",
      "MANUAL_REQUIRED",
      "REVIEWED",
      "CANONICAL_FOLLOWER",
    ]),
    deadlineAt: z.string(),
    lateNotice: z.boolean(),
    reviewTaskMayExist: z.boolean(),
    operationalAuthority: z.literal(false),
    readiness: z
      .object({ questions: z.array(z.object({ slot: z.string(), text: z.string() })).optional() })
      .passthrough()
      .nullable(),
    finalAssessment: z
      .object({
        ai07: z
          .object({
            category: z.string().nullable().optional(),
            priority: z.string().nullable().optional(),
          })
          .passthrough()
          .optional(),
      })
      .passthrough()
      .nullable(),
  })
  .passthrough();

export type Intake = z.infer<typeof schema>;
const path = (workspace: string, id: string) =>
  `/apps/servicedesk/v1/workspaces/${encodeURIComponent(workspace)}/tickets/${encodeURIComponent(id)}`;

export const getIntake = async (workspace: string, id: string, signal?: AbortSignal) => {
  try {
    return schema.parse((await apiClient.get(`${path(workspace, id)}/intake`, { signal })).data);
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) return null;
    throw error;
  }
};

export const evaluateIntake = async (workspace: string, id: string) =>
  schema.parse((await apiClient.post(`${path(workspace, id)}/intake/evaluate`)).data);

export const releaseIntake = async (
  workspace: string,
  id: string,
  state: Intake,
  reason: string,
  operationId: string,
) =>
  schema.parse(
    (
      await apiClient.post(`${path(workspace, id)}/intake/manual-release`, {
        expectedContextRevision: state.contextRevision,
        expectedVersion: state.version,
        operationId,
        reason,
      })
    ).data,
  );

export const reviewIntake = async (
  workspace: string,
  id: string,
  state: Intake,
  category: string,
  priority: string,
  reason: string,
) =>
  apiClient.post(`${path(workspace, id)}/review`, {
    category,
    priority,
    reason,
    assignee: null,
    expectedContextRevision: state.contextRevision,
    expectedIntakeVersion: state.version,
  });

export const preflightIntake = async (workspace: string, id: string) =>
  z
    .object({
      candidateSetDigest: z.string().nullable(),
      candidateSetIncomplete: z.boolean(),
      incidentSignal: z.boolean(),
      candidates: z.array(
        z.object({
          candidateRef: z.string().uuid(),
          contextRevision: z.number(),
          strongUnverified: z.boolean(),
        }),
      ),
    })
    .parse((await apiClient.post(`${path(workspace, id)}/intake/duplicate-preflight`)).data);

export const readCanonical = async (workspace: string, id: string, signal?: AbortSignal) =>
  z
    .object({
      rootTicketId: z.string().uuid().nullable(),
      status: z.string(),
      publicProgress: z.string(),
      version: z.number(),
      category: z.string(),
    })
    .parse((await apiClient.get(`${path(workspace, id)}/canonical`, { signal })).data);

export const canonicalCommand = (
  workspace: string,
  id: string,
  split: boolean,
  command: Record<string, unknown>,
) => apiClient.post(`${path(workspace, id)}/canonical${split ? "/split" : ""}`, command);
