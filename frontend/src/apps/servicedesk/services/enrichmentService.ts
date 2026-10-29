import { z } from "zod";
import { apiClient, ApiError } from "../../../platform/shared/api";

const text = z.string().min(1).max(256);
const digest = z.string().regex(/^[0-9a-f]{64}$/);
const revision = z.number().int().min(0).max(32);
const state = z.enum([
  "READINESS_OK",
  "ENRICHMENT_OPTIONAL",
  "ENRICHMENT_REQUIRED",
  "MANUAL_REVIEW_REQUIRED",
]);

const questionSchema = z
  .object({
    questionId: z.string().regex(/^[0-9a-f]{24}$/),
    slot: text,
    text,
    choices: z.array(text).max(8),
    correctionRequired: z.boolean(),
  })
  .strict();

const readinessSchema = z
  .object({
    contractVersion: z.literal("ticket-readiness@0.1.0"),
    ticketId: z.string().uuid(),
    contextRevision: revision,
    contextDigest: digest.nullable(),
    overall: state.nullable(),
    tasks: z
      .array(
        z
          .object({
            task: z.enum(["ASSESSMENT", "DUPLICATE_CHECK"]),
            state,
            missingSlots: z.array(text).max(32),
            blockedOperations: z
              .array(z.enum(["ASSESSMENT_PROPOSAL", "EXACT_DUPLICATE_ASSERTION"]))
              .max(2),
          })
          .strict(),
      )
      .max(2),
    reasonCodes: z.array(text).max(64),
    nextAction: text,
    questions: z.array(questionSchema).max(3),
    policyDigest: digest,
    needsReview: z.literal(true),
    operationalAuthority: z.literal(false),
    workflowGateApplied: z.literal(false),
  })
  .strict();

export const enrichmentStateSchema = z
  .object({
    contractVersion: z.literal("ticket-enrichment-lifecycle@0.1.0"),
    ticketId: z.string().uuid(),
    contextRevision: revision,
    status: z.enum([
      "NEW",
      "EVALUATING",
      "READY",
      "OPTIONAL_ENRICHMENT",
      "AWAITING_ANSWER",
      "REASSESS_QUEUED",
      "MANUAL_REVIEW",
      "TIMED_OUT",
      "CANCELLED_BY_PROCESS",
    ]),
    version: z.number().int().nonnegative(),
    roundCount: z.number().int().min(0).max(2),
    manualReviewRequired: z.boolean(),
    workflowGateApplied: z.literal(false),
    needsReview: z.literal(true),
    operationalAuthority: z.literal(false),
    deadlineAt: z.string().datetime({ offset: true }).nullable(),
    remindedAt: z.string().datetime({ offset: true }).nullable(),
    requestInstanceId: z.string().uuid().nullable(),
    deliveryStatus: z.enum(["NOT_REQUESTED", "PENDING"]),
    nextAction: text,
    reasonCodes: z.array(text).max(64),
    questions: z.array(questionSchema).max(3),
    readiness: readinessSchema.nullable(),
  })
  .strict();

export type EnrichmentState = z.infer<typeof enrichmentStateSchema>;
export type EnrichmentQuestion = z.infer<typeof questionSchema>;

const evidenceSchema = z
  .object({
    sourceId: text,
    sourceRevision: revision,
    quote: z.string().min(1).max(4000),
    start: z.number().int().nonnegative(),
    end: z.number().int().positive(),
    ruleId: text,
    correction: z.boolean(),
  })
  .strict();

export const contextualAssessmentSchema = z
  .object({
    contractVersion: z.literal("ticket-assessment-contextual@0.1.0"),
    ticketId: z.string().uuid(),
    contextRevision: revision,
    contextDigest: digest.nullable(),
    assessmentStatus: z.enum([
      "SHADOW",
      "DETERMINISTIC_ONLY",
      "ENRICHMENT_REQUIRED",
      "REVIEW_REQUIRED",
      "MODEL_REUSED_MATERIAL",
      "DEGRADED",
    ]),
    dataReadiness: state.nullable(),
    ai07: z
      .object({
        category: z.enum(["ACCOUNT", "HARDWARE", "NETWORK", "SOFTWARE", "OTHER"]).nullable(),
        impact: z.enum(["I1", "I2", "I3", "I4"]).nullable(),
        urgency: z.enum(["U1", "U2", "U3", "U4"]).nullable(),
        priority: z.enum(["LOW", "MEDIUM", "HIGH", "CRITICAL"]).nullable(),
        riskFlags: z.array(text).max(8),
        confidence: z.null(),
        confidenceSemantics: z.literal("NOT_CALIBRATED"),
      })
      .strict()
      .nullable(),
    evidence: z.array(evidenceSchema).max(64),
    reasons: z.array(text).max(64),
    provenance: z
      .object({
        modelDigest: digest.nullable(),
        modelPolicyDigest: digest.nullable(),
        contextualPolicyDigest: digest,
        assessmentPolicyDigest: digest,
        inputDigest: digest,
        evidenceStatus: z.literal("USER_REPORTED_NOT_VERIFIED"),
      })
      .strict()
      .nullable(),
    needsReview: z.literal(true),
    operationalAuthority: z.literal(false),
    workflowGateApplied: z.literal(false),
  })
  .strict();

export type ContextualAssessment = z.infer<typeof contextualAssessmentSchema>;
export interface ClarificationAnswer {
  slot: string;
  value: string;
  correction: boolean;
}
export interface ClarificationInput {
  contractVersion: "ticket-clarification@0.1.0";
  clientAnswerId: string;
  expectedContextRevision: number;
  answers: ClarificationAnswer[];
}

const ticketPath = (workspace: string, id: string) =>
  `/apps/servicedesk/v1/workspaces/${encodeURIComponent(workspace)}/tickets/${encodeURIComponent(id)}`;

const sameTicket = <T extends { ticketId: string }>(value: T, expected: string): T => {
  if (value.ticketId !== expected) throw new Error("Kết quả không thuộc ticket đang xem.");
  return value;
};

export const getContextRevision = async (workspace: string, id: string, signal?: AbortSignal) => {
  const { data } = await apiClient.get<unknown>(`${ticketPath(workspace, id)}/context-revision`, {
    signal,
  });
  const value = z
    .object({
      contractVersion: z.literal("ticket-context-revision@0.1.0"),
      ticketId: z.string().uuid(),
      contextRevision: revision,
    })
    .strict()
    .parse(data);
  return sameTicket(value, id).contextRevision;
};

export const getEnrichmentState = async (
  workspace: string,
  id: string,
  signal?: AbortSignal,
): Promise<EnrichmentState | null> => {
  try {
    const { data } = await apiClient.get<unknown>(`${ticketPath(workspace, id)}/enrichment-state`, {
      signal,
    });
    return sameTicket(enrichmentStateSchema.parse(data), id);
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) return null;
    throw error;
  }
};

export const evaluateEnrichment = async (workspace: string, id: string) => {
  const { data } = await apiClient.post<unknown>(
    `${ticketPath(workspace, id)}/enrichment-state/evaluate`,
    { contractVersion: "ticket-enrichment-lifecycle@0.1.0" },
    { timeout: 12000 },
  );
  return sameTicket(enrichmentStateSchema.parse(data), id);
};

export const appendClarification = async (
  workspace: string,
  id: string,
  input: ClarificationInput,
) => {
  const { data } = await apiClient.post<unknown>(
    `${ticketPath(workspace, id)}/clarifications`,
    input,
    { timeout: 10000 },
  );
  const value = z
    .object({
      contractVersion: z.literal("ticket-clarification@0.1.0"),
      ticketId: z.string().uuid(),
      answerId: z.string().uuid(),
      contextRevision: revision,
      assessmentStatus: z.literal("AI07_REFRESH_REQUIRED"),
      replayed: z.boolean(),
    })
    .strict()
    .parse(data);
  return sameTicket(value, id);
};

export const assessCurrentContext = async (workspace: string, id: string) => {
  const { data } = await apiClient.post<unknown>(
    `${ticketPath(workspace, id)}/assistant/assessment-contextual`,
    { contractVersion: "ticket-assessment-contextual@0.1.0" },
    { timeout: 25000 },
  );
  return sameTicket(contextualAssessmentSchema.parse(data), id);
};
