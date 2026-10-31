const INTERNAL_VALUES = [
  "internal",
  "private",
  "nội bộ",
  "noi bo",
  "internal only",
  "chỉ dùng nội bộ",
];

export const isInternalKnowledge = (value: string) =>
  INTERNAL_VALUES.includes(value.trim().toLocaleLowerCase("vi"));

export const isKnowledgeAccessValue = (value: string) =>
  isInternalKnowledge(value) ||
  ["public", "công khai"].includes(value.trim().toLocaleLowerCase("vi"));
