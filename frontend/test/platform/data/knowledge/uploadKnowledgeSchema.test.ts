import { describe, expect, it } from "vitest";
import {
  MAX_KNOWLEDGE_FILE_BYTES,
  uploadKnowledgeSchema,
  type KnowledgeFormInput,
  type KnowledgeFormOutput,
} from "../../../../src/platform/data/knowledge/uploadKnowledgeSchema";

const fields = { title: " Guide ", source: "synthetic", version: "1", usageRights: "synthetic" };

describe("knowledge upload input and parsed output", () => {
  it("permits an empty form value but rejects submission without a file", () => {
    const input: KnowledgeFormInput = { ...fields, file: null };
    expect(uploadKnowledgeSchema.safeParse(input).success).toBe(false);
  });

  it("produces a non-null File and trimmed fields after validation", () => {
    const file = new File(["Synthetic guide"], "guide.md", { type: "text/markdown" });
    const parsed: KnowledgeFormOutput = uploadKnowledgeSchema.parse({ ...fields, file });
    const requiredFile: File = parsed.file;
    expect(requiredFile).toBe(file);
    expect(parsed.title).toBe("Guide");
  });

  it("rejects unsupported extensions and oversized uploads", () => {
    for (const file of [
      new File(["not text"], "image.png"),
      new File([new Uint8Array(MAX_KNOWLEDGE_FILE_BYTES + 1)], "oversized.txt"),
    ]) {
      expect(uploadKnowledgeSchema.safeParse({ ...fields, file }).success).toBe(false);
    }
  });
});
