import { z } from "zod";
import { ApiError } from "@/platform/shared/api";

// Envelope gợi ý chung (contracts/intelligence/v1/triage-result.schema.json); trường của `result` do profile của app quyết định
const evidence = z.object({
  type: z.string().min(1),
  field: z.string().min(1),
  text: z.string().min(1),
});

const explanation = z.object({
  matchedRules: z.array(z.string()),
  reasonCode: z.string().min(1),
  text: z.string().min(1),
});

const runtime = z.object({
  engine: z.string().min(1),
  profileVersion: z.string().min(1),
  rulesVersion: z.string().optional(),
  modelVersion: z.string().optional(),
});

const envelope = z
  .object({
    requestId: z.string().min(1),
    status: z.enum(["SUCCESS", "DEGRADED"]),
    result: z.record(z.string(), z.string()).nullable(),
    evidence: z.array(evidence),
    explanation: explanation.nullable(),
    runtime: runtime.nullable(),
    needsReview: z.boolean(),
  })
  .superRefine((value, context) => {
    // SUCCESS phải có kết quả; DEGRADED không được kèm kết quả hay bằng chứng
    const valid =
      value.status === "SUCCESS"
        ? value.result !== null && value.explanation !== null
        : value.result === null && value.evidence.length === 0 && value.needsReview;
    if (!valid) {
      context.addIssue({ code: z.ZodIssueCode.custom, message: "Invalid suggestion state" });
    }
  });

/** Gợi ý của AI cho một bản ghi: kết quả, bằng chứng, giải thích. Chỉ để tham khảo. */
export type Suggestion = z.infer<typeof envelope>;

/** Kiểm tra phản hồi gợi ý; sai hợp đồng thì coi như 502, không hiện gì. */
export const parseSuggestion = (value: unknown): Suggestion => {
  const parsed = envelope.safeParse(value);
  if (!parsed.success) throw new ApiError(502, { title: "SUGGESTION_CONTRACT_FAILED" }, null);

  return parsed.data;
};
