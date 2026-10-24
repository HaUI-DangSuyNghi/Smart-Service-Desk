import type { KeyboardEvent } from "react";

/**
 * Gắn vào `onKeyDown` của `<form>`: Enter gửi form ở mọi ô, kể cả ô chọn dạng thẻ (radio).
 * Ô nhiều dòng thì Enter xuống dòng, Ctrl/⌘ + Enter mới gửi. Nút, ô chọn file giữ hành vi riêng.
 * Gửi qua nút submit của form (kể cả nút nằm ngoài form, gắn bằng `form="id"`): nút đang khóa thì không gửi trùng.
 */
export const submitOnEnter = (event: KeyboardEvent<HTMLFormElement>) => {
  if (event.key !== "Enter" || event.nativeEvent.isComposing) return;
  const target = event.target;
  if (target instanceof HTMLButtonElement || target instanceof HTMLAnchorElement) return;
  if (target instanceof HTMLInputElement && target.type === "file") return;
  if (target instanceof HTMLTextAreaElement && !(event.ctrlKey || event.metaKey)) return;

  event.preventDefault();
  const form = event.currentTarget;
  const submitter = Array.from(form.elements).find(
    (element): element is HTMLButtonElement =>
      element instanceof HTMLButtonElement && element.type === "submit",
  );
  if (submitter?.disabled) return;
  form.requestSubmit(submitter);
};
