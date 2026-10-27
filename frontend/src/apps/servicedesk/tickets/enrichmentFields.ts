/** Danh mục nhập liệu có giới hạn, không có category/priority hoặc field thao tác workflow. */
export const ENRICHMENT_FIELDS: Record<string, { label: string; choices?: [string, string][] }> = {
  "device.identifier": { label: "Mã hoặc số thiết bị" },
  "location.room": { label: "Phòng xảy ra sự cố" },
  "device.type": {
    label: "Thiết bị hoặc dịch vụ",
    choices: [
      ["PC", "Máy tính"],
      ["PROJECTOR", "Máy chiếu"],
      ["PRINTER", "Máy in"],
      ["WIFI", "Wi-Fi"],
    ],
  },
  symptomCodes: {
    label: "Biểu hiện sự cố",
    choices: [
      ["POWER_FAILURE", "Không lên nguồn"],
      ["NETWORK_UNAVAILABLE", "Không kết nối mạng"],
      ["DISPLAY_FAILURE", "Không hiển thị"],
    ],
  },
  affectedScope: {
    label: "Phạm vi ảnh hưởng",
    choices: [
      ["INDIVIDUAL", "Một người hoặc thiết bị"],
      ["MULTIPLE", "Nhiều người hoặc thiết bị"],
      ["WHOLE_ROOM", "Cả phòng"],
    ],
  },
  affectedCount: { label: "Số người hoặc thiết bị bị ảnh hưởng" },
  workBlocked: {
    label: "Công việc có đang bị dừng không?",
    choices: [
      ["có", "Có"],
      ["không", "Không"],
    ],
  },
  workaroundAvailable: {
    label: "Có cách làm thay thế không?",
    choices: [
      ["có", "Có"],
      ["không", "Không"],
    ],
  },
  timeWindow: { label: "Mốc thời gian cần lưu ý" },
};

export const ENRICHMENT_STATUS: Record<string, string> = {
  NEW: "Chờ kiểm tra thông tin",
  EVALUATING: "Đang kiểm tra thông tin",
  READY: "Đã đủ thông tin để xem xét",
  OPTIONAL_ENRICHMENT: "Có thể bổ sung thêm",
  AWAITING_ANSWER: "Cần bổ sung thông tin",
  REASSESS_QUEUED: "Thông tin mới đang chờ kiểm tra",
  MANUAL_REVIEW: "Cần điều phối viên xem xét",
  TIMED_OUT: "Đã chuyển điều phối viên vì hết thời gian chờ",
  CANCELLED_BY_PROCESS: "Quy trình hiện tại đã kết thúc hoặc không còn hoạt động",
};
