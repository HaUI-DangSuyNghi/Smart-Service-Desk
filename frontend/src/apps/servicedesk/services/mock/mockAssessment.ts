import {
  DEFAULT_CATEGORY_GROUPS,
  priorityOf,
  type Category,
  type Impact,
  type Urgency,
} from "../../tickets/ticketRules";
import type { Assessment } from "../ticketService";

// AI giả: phân loại theo từ khóa, trả đúng dạng kết quả AI07 (giải thích, câu hỏi gợi ý, ABSTAIN)

const CATEGORY_KEYWORDS: readonly [Category, readonly string[]][] = [
  ["ACCOUNT", ["mật khẩu", "tài khoản", "đăng nhập", "email"]],
  ["NETWORK", ["mạng", "wi-fi", "wifi", "internet"]],
  ["SOFTWARE", ["phần mềm", "cài", "office", "matlab", "ứng dụng"]],
  ["HARDWARE", ["nguồn", "màn hình", "bàn phím", "chuột", "máy chiếu", "ổ cứng"]],
  ["OTHER", ["điều hòa", "đèn", "bàn ghế"]],
];

const IMPACT_KEYWORDS: readonly [Impact, readonly string[]][] = [
  ["I4", ["nhiều phòng", "cả tầng", "tầng"]],
  ["I3", ["cả phòng", "phòng thi", "toàn phòng"]],
  ["I2", ["vài máy", "nhiều máy"]],
];

const URGENCY_KEYWORDS: readonly [Urgency, readonly string[]][] = [
  ["U4", ["thi sau", "gấp", "ngay"]],
  ["U3", ["không lên", "không vào", "không dùng", "không cài", "liệt"]],
  ["U1", ["vẫn dùng được", "tạm"]],
];

/** Hai câu cố định khi thiếu thông tin, như AI07. */
export const MISSING_INFO_QUESTIONS = [
  "Thiết bị hoặc dịch vụ nào bị ảnh hưởng?",
  "Có bao nhiêu người bị ảnh hưởng và công việc có đang bị chặn không?",
];

interface Match<T> {
  value: T;
  field: "title" | "description";
  text: string;
}

/** Từ khóa đầu tiên khớp, kèm đoạn nguyên văn trong tiêu đề hoặc mô tả làm bằng chứng. */
const findFirst = <T>(
  table: readonly [T, readonly string[]][],
  fields: { title: string; description: string },
): Match<T> | null => {
  for (const [value, keywords] of table) {
    for (const keyword of keywords) {
      for (const field of ["title", "description"] as const) {
        const index = fields[field].toLowerCase().indexOf(keyword);
        if (index >= 0) {
          return { value, field, text: fields[field].slice(index, index + keyword.length) };
        }
      }
    }
  }
  return null;
};

export const assessText = (title: string, description: string): Assessment => {
  const fields = { title, description };
  const category = findFirst(CATEGORY_KEYWORDS, fields);
  const assessedAt = new Date().toISOString();

  if (!category) {
    return {
      status: "READY",
      category: null,
      impact: null,
      urgency: null,
      priority: null,
      suggestedGroup: null,
      reasonCode: "INSUFFICIENT_INFORMATION",
      needsReview: true,
      explanation: null,
      evidence: [],
      nextQuestions: MISSING_INFO_QUESTIONS,
      profileVersion: "service-desk@0.3.0",
      assessedAt,
    };
  }

  const impact = findFirst(IMPACT_KEYWORDS, fields);
  const urgency = findFirst(URGENCY_KEYWORDS, fields);
  const impactValue = impact?.value ?? "I1";
  const urgencyValue = urgency?.value ?? "U2";
  const priority = priorityOf(impactValue, urgencyValue);
  const evidence = [category, impact, urgency]
    .filter((match) => match !== null)
    .map((match) => ({ field: match.field, text: match.text }));

  return {
    status: "READY",
    category: category.value,
    impact: impactValue,
    urgency: urgencyValue,
    priority,
    suggestedGroup: DEFAULT_CATEGORY_GROUPS[category.value],
    reasonCode: "CLASSIFIED",
    needsReview: category.value === "OTHER",
    explanation: {
      classificationReason: `Các quy tắc và bằng chứng đầu vào gợi ý nhóm ${category.value}. Người xử lý cần xác nhận.`,
      priorityReason: `Mức ${priority} theo ma trận tác động ${impactValue} và khẩn cấp ${urgencyValue}.`,
    },
    evidence,
    nextQuestions: [],
    profileVersion: "service-desk@0.3.0",
    assessedAt,
  };
};

/** AI lỗi hoặc đang tắt: điều phối viên tự chọn. */
export const unavailableAssessment = (): Assessment => ({
  status: "DEGRADED",
  category: null,
  reasonCode: "INTELLIGENCE_UNAVAILABLE",
  needsReview: true,
  explanation: null,
  evidence: [],
  nextQuestions: [],
  assessedAt: new Date().toISOString(),
});
