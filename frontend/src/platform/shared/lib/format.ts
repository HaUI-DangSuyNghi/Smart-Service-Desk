const DATE_TIME = new Intl.DateTimeFormat("vi-VN", { dateStyle: "short", timeStyle: "short" });

/** "2026-10-24T08:00:00Z" → "24/10/2026, 15:00" theo giờ máy người dùng. */
export const formatDateTime = (iso: string) => DATE_TIME.format(new Date(iso));

/** 1536 → "1,5 KB". */
export const formatFileSize = (bytes: number) => {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024)
    return `${(bytes / 1024).toLocaleString("vi-VN", { maximumFractionDigits: 1 })} KB`;
  return `${(bytes / 1024 / 1024).toLocaleString("vi-VN", { maximumFractionDigits: 1 })} MB`;
};
