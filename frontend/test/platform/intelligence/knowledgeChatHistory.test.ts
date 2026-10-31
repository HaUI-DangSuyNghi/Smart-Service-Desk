import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  historyKey,
  loadHistory,
  saveHistory,
  type Conversation,
} from "../../../src/platform/intelligence/knowledgeChatHistory";

const key = historyKey("issuer", "user1", "lab-a");
const makeConversation = (): Conversation => ({
  id: crypto.randomUUID(),
  documentId: "",
  updatedAt: Date.now(),
  turns: [
    {
      id: crypto.randomUUID(),
      question: "Question",
      answer: "Answer [1]",
      state: "done",
      grounded: true,
      sources: [
        {
          index: 1,
          metadataId: crypto.randomUUID(),
          title: "Document",
          version: "1",
          excerpt: "Exact evidence",
        },
      ],
    },
  ],
});

describe("local knowledge chat history", () => {
  let values: Map<string, string>;
  beforeEach(() => {
    values = new Map();
    vi.stubGlobal("localStorage", {
      getItem: (name: string) => values.get(name) ?? null,
      setItem: (name: string, value: string) => values.set(name, value),
      removeItem: (name: string) => values.delete(name),
    });
  });
  afterEach(() => vi.unstubAllGlobals());

  it("round-trips answers, exact citations and document selection", () => {
    const item = makeConversation();
    item.documentId = crypto.randomUUID();
    expect(saveHistory(key, [item])).toBe(true);
    expect(loadHistory(key)).toEqual({ conversations: [item], failed: false });
  });
  it("isolates accounts, workspaces and issuers", () => {
    saveHistory(key, [makeConversation()]);
    for (const other of [
      historyKey("issuer", "user2", "lab-a"),
      historyKey("issuer", "user1", "lab-b"),
      historyKey("other", "user1", "lab-a"),
    ]) {
      expect(loadHistory(other).conversations).toEqual([]);
    }
  });
  it("restores interrupted streams as stopped, not verified answers", () => {
    const item = makeConversation();
    item.turns[0].state = "streaming";
    saveHistory(key, [item]);
    expect(loadHistory(key).conversations[0].turns[0]).toMatchObject({
      state: "stopped",
      grounded: undefined,
    });
  });
  it("rejects malformed or oversized storage without deleting it", () => {
    for (const raw of [
      "broken",
      '{"version":2,"conversations":[]}',
      JSON.stringify({
        version: 1,
        conversations: [{ ...makeConversation(), turns: [{ question: "invalid" }] }],
      }),
      "x".repeat(1_000_001),
    ]) {
      values.set(key, raw);
      expect(loadHistory(key)).toEqual({ conversations: [], failed: true });
      expect(values.get(key)).toBe(raw);
    }
  });
  it("bounds retention to twenty conversations and one hundred turns", () => {
    const items = Array.from({ length: 25 }, makeConversation);
    items[0].turns = Array.from({ length: 105 }, () => makeConversation().turns[0]);
    expect(saveHistory(key, items)).toBe(true);
    const restored = loadHistory(key).conversations;
    expect(restored).toHaveLength(20);
    expect(restored[0].turns).toHaveLength(100);
    expect(restored[0].turns[0]).toEqual(items[0].turns[5]);
  });
  it("evicts old conversations for size, and preserves old data if one is too large", () => {
    const large = makeConversation();
    large.turns = Array.from({ length: 30 }, () => ({
      ...makeConversation().turns[0],
      answer: "a".repeat(20000),
    }));
    expect(saveHistory(key, [large, structuredClone(large)])).toBe(true);
    expect(loadHistory(key).conversations).toHaveLength(1);
    const stored = values.get(key);
    large.turns = Array.from({ length: 100 }, () => ({
      ...makeConversation().turns[0],
      answer: "a".repeat(20000),
    }));
    expect(saveHistory(key, [large])).toBe(false);
    expect(values.get(key)).toBe(stored);
  });
  it("handles unavailable storage and quota errors without crashing", () => {
    vi.stubGlobal("localStorage", {
      getItem: () => {
        throw Error("Denied");
      },
      setItem: () => {
        throw Error("Quota");
      },
    });
    expect(loadHistory(key).failed).toBe(true);
    expect(saveHistory(key, [makeConversation()])).toBe(false);
  });
  it("removes only this account and workspace history on deletion", () => {
    const other = historyKey("issuer", "user2", "lab-a");
    saveHistory(key, [makeConversation()]);
    saveHistory(other, [makeConversation()]);
    expect(saveHistory(key, [])).toBe(true);
    expect(values.has(key)).toBe(false);
    expect(loadHistory(other).conversations).toHaveLength(1);
  });
});
