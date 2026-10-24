import type { ProcessStartField } from "@/platform/services";

export interface RequestContentItem {
  key: string;
  label: string;
  value: string;
}

/**
 * Nội dung người tạo đã nhập, theo thứ tự ô của form.
 * Biến không có trong danh mục (quy trình đã đổi ô, danh mục chưa tải) vẫn hiện, nhãn là key, xếp cuối.
 */
export const requestContent = (
  fields: readonly ProcessStartField[],
  variables: Readonly<Record<string, string>>,
): RequestContentItem[] => {
  const labelled = new Set(fields.map((field) => field.key));
  const known = fields.flatMap((field) => {
    const value = variables[field.key];
    return value === undefined ? [] : [{ key: field.key, label: field.label, value }];
  });
  const unknown = Object.entries(variables)
    .filter(([key]) => !labelled.has(key))
    .sort(([a], [b]) => a.localeCompare(b))
    .map(([key, value]) => ({ key, label: key, value }));
  return [...known, ...unknown];
};
