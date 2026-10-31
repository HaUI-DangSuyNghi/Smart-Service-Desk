import { z } from "zod";
import { apiClient } from "@/platform/shared/api";
import { hpdiWorkspace } from "./paths";

export const sourceSchema = z
  .object({
    index: z.number().int().min(1).max(5),
    metadataId: z.string().uuid(),
    title: z.string().max(200),
    version: z.string().max(100),
    excerpt: z.string().min(1).max(2400),
  })
  .strict();
const eventSchema = z.discriminatedUnion("type", [
  z.object({ type: z.literal("status"), text: z.string() }).strict(),
  z.object({ type: z.literal("sources"), sources: z.array(sourceSchema).max(5) }).strict(),
  z.object({ type: z.literal("delta"), text: z.string().max(20000) }).strict(),
  z.object({ type: z.literal("done"), grounded: z.boolean() }).strict(),
  z.object({ type: z.literal("error"), code: z.string() }).strict(),
]);
export type KnowledgeSource = z.infer<typeof sourceSchema>;
export type KnowledgeEvent = z.infer<typeof eventSchema>;

export const readKnowledgeStream = async (
  body: ReadableStream<Uint8Array>,
  onEvent: (event: KnowledgeEvent) => void,
) => {
  const reader = body.getReader();
  const decoder = new TextDecoder("utf-8", { fatal: true });
  let buffer = "";
  let done = false;
  let characters = 0;
  try {
    for (;;) {
      const result = await reader.read();
      buffer += decoder.decode(result.value, { stream: !result.done });
      let boundary: number;
      while ((boundary = buffer.indexOf("\n\n")) >= 0) {
        const frame = buffer.slice(0, boundary);
        buffer = buffer.slice(boundary + 2);
        if (frame.length > 65536 || done) throw new Error("Luồng trả lời không hợp lệ.");
        const data = frame
          .split("\n")
          .filter((line) => line.startsWith("data:"))
          .map((line) => line.slice(5).trimStart())
          .join("\n");
        if (!data) continue;
        const event = eventSchema.parse(JSON.parse(data));
        if (event.type === "error")
          throw new Error(
            "AI tạm thời không trả lời được. Bạn vẫn có thể mở các đoạn nguồn để đọc.",
          );
        if (event.type === "delta") {
          characters += event.text.length;
          if (characters > 20000) throw new Error("Câu trả lời vượt giới hạn.");
        }
        if (event.type === "done") done = true;
        onEvent(event);
      }
      if (buffer.length > 65536) throw new Error("Luồng trả lời vượt giới hạn.");
      if (result.done) break;
    }
    if (!done || buffer.trim())
      throw new Error("Kết nối bị ngắt trước khi trả lời hoàn tất. Hãy thử lại.");
  } finally {
    await reader.cancel().catch(() => undefined);
    reader.releaseLock();
  }
};

export const askKnowledge = async (
  workspace: string,
  question: string,
  documentIds: string[],
  onEvent: (event: KnowledgeEvent) => void,
  signal: AbortSignal,
) => {
  const response = await apiClient.post<ReadableStream<Uint8Array>>(
    `${hpdiWorkspace(workspace)}/knowledge/chat`,
    { question, documentIds },
    { adapter: "fetch", responseType: "stream", signal, headers: { Accept: "text/event-stream" } },
  );
  if (!String(response.headers["content-type"]).startsWith("text/event-stream")) {
    await response.data.cancel().catch(() => undefined);
    throw new Error("Máy chủ không hỗ trợ luồng trả lời.");
  }
  await readKnowledgeStream(response.data, onEvent);
};
