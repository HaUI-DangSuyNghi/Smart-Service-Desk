import { z } from "zod";
import { sourceSchema } from "@/platform/services/knowledgeChatService";

const turnSchema = z.object({
  id: z.string().uuid(),
  question: z.string().min(1).max(2000),
  answer: z.string().max(20000),
  sources: z.array(sourceSchema).max(5),
  state: z.enum(["searching", "streaming", "done", "error", "stopped"]),
  error: z.string().max(2000).optional(),
  grounded: z.boolean().optional(),
});
const conversationSchema = z.object({
  id: z.string().uuid(),
  documentId: z.union([z.literal(""), z.string().uuid()]),
  updatedAt: z.number().finite(),
  turns: z.array(turnSchema).min(1).max(100),
});
const historySchema = z.object({
  version: z.literal(1),
  conversations: z.array(conversationSchema).max(20),
});

export type Turn = z.infer<typeof turnSchema>;
export type Conversation = z.infer<typeof conversationSchema>;
// ponytail: browser localStorage capped at 20 conversations / 1MB; upgrade to server DB when cross-device sync is requested.
const MAX_CHARACTERS = 1_000_000;

export const historyKey = (issuer: string, user: string, workspace: string) =>
  `knowledge-chat:v1:${JSON.stringify([issuer, user, workspace])}`;

export function loadHistory(key: string): { conversations: Conversation[]; failed: boolean } {
  try {
    const raw = localStorage.getItem(key);
    if (!raw) return { conversations: [], failed: false };
    if (raw.length > MAX_CHARACTERS) throw new Error("History too large");
    const data = historySchema.parse(JSON.parse(raw));
    return {
      conversations: data.conversations.map((conversation) => ({
        ...conversation,
        turns: conversation.turns.map((turn) =>
          turn.state === "searching" || turn.state === "streaming"
            ? { ...turn, state: "stopped" as const, grounded: undefined }
            : turn,
        ),
      })),
      failed: false,
    };
  } catch {
    return { conversations: [], failed: true };
  }
}

export function saveHistory(key: string, conversations: Conversation[]): boolean {
  try {
    if (!conversations.length) {
      localStorage.removeItem(key);
      return true;
    }
    const kept = conversations.slice(0, 20).map((conversation) => ({
      ...conversation,
      turns: conversation.turns.slice(-100),
    }));
    let raw = JSON.stringify({ version: 1, conversations: kept });
    while (raw.length > MAX_CHARACTERS && kept.length > 1) {
      kept.pop();
      raw = JSON.stringify({ version: 1, conversations: kept });
    }
    if (raw.length > MAX_CHARACTERS) return false;
    historySchema.parse(JSON.parse(raw));
    localStorage.setItem(key, raw);
    return true;
  } catch {
    return false;
  }
}
