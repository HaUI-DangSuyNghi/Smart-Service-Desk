import { z } from "zod";
import { ApiError, apiClient } from "../../../platform/shared/api";

const citation = z
  .object({
    citationId: z.string().regex(/^C[1-3]$/),
    displayTitle: z.string().min(1).max(200),
    sourceVersion: z.string().min(1).max(100),
  })
  .strict();

const envelope = z
  .object({
    contractVersion: z.literal("0.1.0"),
    requestId: z.string().uuid(),
    status: z.enum(["SUCCESS", "INSUFFICIENT_EVIDENCE"]),
    answer: z.string().min(1).max(16000).nullable(),
    citations: z.array(citation).max(3),
    needsReview: z.literal(true),
    reasonCode: z.enum(["GROUNDED", "INSUFFICIENT_EVIDENCE"]),
  })
  .strict()
  .superRefine((value, context) => {
    const success = value.status === "SUCCESS";
    const valid = success
      ? value.answer !== null && value.citations.length > 0 && value.reasonCode === "GROUNDED"
      : value.answer === null &&
        value.citations.length === 0 &&
        value.reasonCode === "INSUFFICIENT_EVIDENCE";

    if (
      !valid ||
      new Set(value.citations.map((item) => item.citationId)).size !== value.citations.length
    ) {
      context.addIssue({ code: z.ZodIssueCode.custom, message: "Invalid assistance state" });
    }
  });

export type Assistance = z.infer<typeof envelope>;

export function parseAssistance(value: unknown): Assistance {
  const parsed = envelope.safeParse(value);
  if (!parsed.success) throw new ApiError(502, { title: "ASSISTANCE_CONTRACT_FAILED" }, null);

  return parsed.data;
}

export async function askAssistance(
  workspace: string,
  ticketId: string,
  question: string,
  signal: AbortSignal,
): Promise<Assistance> {
  const path = `/apps/servicedesk/v1/workspaces/${encodeURIComponent(workspace)}/tickets/${encodeURIComponent(ticketId)}/assistance`;
  const { data } = await apiClient.post<unknown>(
    path,
    { question },
    {
      signal,
      timeout: 18000,
      headers: { "Cache-Control": "no-store" },
    },
  );

  return parseAssistance(data);
}
