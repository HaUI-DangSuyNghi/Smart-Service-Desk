// Hỏi thêm khi báo sự cố (giai đoạn 1, chạy ở FE). Mục và giá trị khớp backend:
// context-slots-v0.1.json (regex, giá trị hợp lệ), enrichment-questions-v0.1.json (lời hỏi).

export type SlotKey = "location.room" | "affectedScope" | "device.identifier" | "workBlocked";

export interface Choice {
  label: string;
  value: string;
}

export interface SlotQuestion {
  slot: SlotKey;
  text: string;
  /** Có thì hiện nút chọn nhanh; không thì cho gõ. */
  choices?: readonly Choice[];
  placeholder?: string;
}

/** Câu trả lời đã nhận; null là người dùng bỏ qua. */
export type Answers = Partial<Record<SlotKey, string | null>>;

export const QUESTIONS: Readonly<Record<SlotKey, SlotQuestion>> = {
  "location.room": {
    slot: "location.room",
    text: "Sự cố đang xảy ra ở phòng nào?",
    placeholder: "Ví dụ: 1304",
  },
  affectedScope: {
    slot: "affectedScope",
    text: "Sự cố ảnh hưởng một người, một thiết bị hay cả phòng?",
    choices: [
      { label: "Một máy / một người", value: "INDIVIDUAL" },
      { label: "Vài máy", value: "MULTIPLE" },
      { label: "Cả phòng", value: "WHOLE_ROOM" },
    ],
  },
  "device.identifier": {
    slot: "device.identifier",
    text: "Thiết bị gặp lỗi có mã hoặc số máy nào?",
    placeholder: "Ví dụ: 15",
  },
  workBlocked: {
    slot: "workBlocked",
    text: "Sự cố có khiến công việc hiện tại bị dừng không?",
    choices: [
      { label: "Có, đang bị dừng việc", value: "true" },
      { label: "Chưa, vẫn làm tạm được", value: "false" },
    ],
  },
};

// Thứ tự hỏi: vị trí trước, rồi phạm vi, số máy (chỉ khi lỗi ở một thiết bị), mức chặn việc
const ORDER: readonly SlotKey[] = ["location.room", "affectedScope", "device.identifier", "workBlocked"];

// Regex lấy từ context-slots-v0.1.json để FE hiểu câu giống backend
const ROOM = /(?:phòng|phong|room|ở)\s*[:#-]?\s*([a-z]?\d{2,6}(?:[.-][0-9a-z]{1,6})?)\b/iu;
const DEVICE_ID =
  /\b(?:máy(?: tính)?|may(?: tinh)?|pc|computer)\s*(?:số|so|#|no\.?)?\s*[-:]?\s*(\d{1,6}|[a-z]{1,8}[-_]\d{1,8})\b/iu;
const DEVICE = /\b(?:máy|may|pc|computer|laptop)\b|màn hình|wi-?fi/iu;
const WHOLE_ROOM = /(?:cả|toàn|tất cả)\s+(?:phòng|lớp)/iu;
const MANY = /(?:vài|nhiều|mấy|các)\s+máy|\b(?:[2-9]|[1-9]\d+)\s*máy/iu;
const ONLY_ONE = /(?:chỉ|một|1)\s+(?:máy|người|mình)/iu;
const BLOCKED = /gấp|khẩn|không (?:làm|học|thi) (?:được|việc)|bị (?:chặn|dừng)|đang thi|sắp thi|không thể/iu;

/** Thông tin đã có sẵn trong câu mô tả, khỏi hỏi lại. */
export const detectSlots = (text: string): Partial<Record<SlotKey, string>> => {
  const found: Partial<Record<SlotKey, string>> = {};
  const room = ROOM.exec(text)?.[1];
  if (room) found["location.room"] = room.toUpperCase();
  const device = DEVICE_ID.exec(text)?.[1];
  if (device) found["device.identifier"] = device.toUpperCase();
  if (WHOLE_ROOM.test(text)) found.affectedScope = "WHOLE_ROOM";
  else if (MANY.test(text)) found.affectedScope = "MULTIPLE";
  else if (ONLY_ONE.test(text)) found.affectedScope = "INDIVIDUAL";
  if (BLOCKED.test(text)) found.workBlocked = "true";
  return found;
};

/** Câu hỏi tiếp theo; null khi đã đủ (hoặc người dùng bỏ qua hết). */
export const nextQuestion = (problem: string, answers: Answers): SlotQuestion | null => {
  const known = { ...detectSlots(problem), ...answers };
  for (const slot of ORDER) {
    if (slot in known) continue;
    // Số máy chỉ hỏi khi nói về thiết bị và lỗi ở một chỗ
    if (
      slot === "device.identifier" &&
      (!DEVICE.test(problem) || (known.affectedScope ?? "INDIVIDUAL") !== "INDIVIDUAL")
    )
      continue;
    return QUESTIONS[slot];
  }
  return null;
};

/** Câu người dùng gõ → giá trị lưu ("phòng 1304" → "1304"); giữ nguyên chữ nếu không nhận ra. */
export const normalizeAnswer = (slot: SlotKey, raw: string) => {
  const text = raw.trim();
  if (slot === "location.room") {
    const room = /([a-z]?\d{2,6}(?:[.-][0-9a-z]{1,6})?)/iu.exec(text)?.[1];
    return room ? room.toUpperCase() : text;
  }
  if (slot === "device.identifier") {
    const id = /(\d{1,6}|[a-z]{1,8}[-_]\d{1,8})/iu.exec(text)?.[1];
    return id ? id.toUpperCase() : text;
  }
  return text;
};
