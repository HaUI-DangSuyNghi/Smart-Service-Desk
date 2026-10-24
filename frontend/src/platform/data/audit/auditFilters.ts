/** Thao tác lọc được trên trang Nhật ký; thao tác riêng của app vẫn hiện, chỉ không có trong bộ lọc. */
export const AUDIT_ACTIONS = [
  { value: "process.start", label: "Tạo yêu cầu" },
  { value: "task.claim", label: "Nhận việc" },
  { value: "task.assign", label: "Giao việc" },
  { value: "task.complete", label: "Xử lý bước" },
  { value: "metadata.create", label: "Tạo tài liệu" },
  { value: "file.upload", label: "Tải file lên" },
  { value: "file.link", label: "Gắn file vào tài liệu" },
] as const;

/** Khoảng thời gian lọc nhật ký; "all" = không giới hạn. */
export const AUDIT_PERIODS = [
  { key: "1", label: "Hôm nay", days: 1 },
  { key: "7", label: "7 ngày", days: 7 },
  { key: "30", label: "30 ngày", days: 30 },
  { key: "all", label: "Tất cả", days: null },
] as const;

export type AuditPeriodKey = (typeof AUDIT_PERIODS)[number]["key"];
