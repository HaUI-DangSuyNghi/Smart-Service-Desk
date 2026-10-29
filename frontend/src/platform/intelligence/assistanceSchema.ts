import { z } from "zod";
import { ApiError } from "@/platform/shared/api";

// Khớp AssistanceResponse (contracts/intelligence/v1/assistance-result.schema.json); field lạ là lỗi hợp đồng
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

// API advice has its own version; it must never masquerade as verified source quotations.
const advice = z
  .object({
    contractVersion: z.literal("0.2.0"),
    requestId: z.string().uuid(),
    status: z.literal("SUCCESS"),
    answer: z.string().trim().min(1).max(16000),
    citations: z.array(citation).length(0),
    needsReview: z.literal(true),
    reasonCode: z.literal("MODEL_ADVICE"),
  })
  .strict();
const response = z.union([envelope, advice]);

/** Verified quotations or explicitly labelled model advice. */
export type Assistance = z.infer<typeof response>;

/** Kiểm tra phản hồi trợ lý; sai hợp đồng thì coi như 502, không hiện gì. */
export const parseAssistance = (value: unknown): Assistance => {
  const parsed = response.safeParse(value);
  if (!parsed.success) throw new ApiError(502, { title: "ASSISTANCE_CONTRACT_FAILED" }, null);

  return parsed.data;
};
