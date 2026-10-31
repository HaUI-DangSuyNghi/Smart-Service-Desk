import { describe, expect, it, vi } from "vitest";
import { apiClient } from "../../../src/platform/shared/api";
import {
  readKnowledgeStream,
  askKnowledge,
  type KnowledgeEvent,
} from "../../../src/platform/services/knowledgeChatService";

const body = (text: string, step = 1) => {
  const bytes = new TextEncoder().encode(text);
  return new ReadableStream<Uint8Array>({
    start(controller) {
      for (let offset = 0; offset < bytes.length; offset += step)
        controller.enqueue(bytes.slice(offset, offset + step));
      controller.close();
    },
  });
};
const frame = (event: KnowledgeEvent) => `data: ${JSON.stringify(event)}\n\n`;
describe("knowledge streaming", () => {
  it("cancels an unexpected response body instead of leaving generation running", async () => {
    const cancel = vi.fn();
    const stream = new ReadableStream<Uint8Array>({ cancel });
    const post = vi.spyOn(apiClient, "post").mockResolvedValue({
      data: stream,
      headers: { "content-type": "text/html" },
    });
    try {
      await expect(
        askKnowledge("lab-a", "Question", [], () => undefined, new AbortController().signal),
      ).rejects.toThrow();
      expect(cancel).toHaveBeenCalledOnce();
    } finally {
      post.mockRestore();
    }
  });
  it("cancels and unlocks the reader after a provider error", async () => {
    const cancel = vi.fn();
    const stream = new ReadableStream<Uint8Array>({
      start(controller) {
        controller.enqueue(new TextEncoder().encode(frame({ type: "error", code: "UNAVAILABLE" })));
      },
      cancel,
    });
    await expect(readKnowledgeStream(stream, () => undefined)).rejects.toThrow();
    expect(cancel).toHaveBeenCalledOnce();
    expect(stream.locked).toBe(false);
  });
  it("decodes Vietnamese across arbitrary UTF-8 chunks and requires completion", async () => {
    const events: KnowledgeEvent[] = [];
    const input = [
      { type: "sources", sources: [] },
      { type: "delta", text: "Tài liệu có nguồn" },
      { type: "done", grounded: false },
    ] satisfies KnowledgeEvent[];
    await readKnowledgeStream(body(input.map(frame).join("")), (event) => events.push(event));
    expect(events).toEqual(input);
  });
  it("rejects truncated streams, upstream errors, forged schemas and post-completion data", async () => {
    for (const input of [
      frame({ type: "delta", text: "Partial" }),
      frame({ type: "error", code: "UNAVAILABLE" }),
      'data: {"type":"sources","sources":[{"metadataId":"bad"}]}\n\n',
      frame({ type: "done", grounded: false }) + frame({ type: "delta", text: "Extra" }),
    ]) {
      await expect(readKnowledgeStream(body(input, 23), () => undefined)).rejects.toThrow();
    }
  });
});
