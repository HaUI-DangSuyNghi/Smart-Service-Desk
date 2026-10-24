/** Hiện khi không tra được tên (tài khoản đã bị xóa/tạo lại, hoặc danh bạ không phản hồi). */
export const UNKNOWN_PERSON = "Không rõ tên";

/** Họ tên theo subject; không tra được thì hiện "Không rõ tên" thay vì mã khó đọc. */
export const personName = (names: ReadonlyMap<string, string>, subject: string) =>
  names.get(subject) ?? UNKNOWN_PERSON;
