import type { NextTaskSpec, Priority } from "../../../../platform/services";
import type { Category } from "../../tickets/ticketRules";
import type { Assessment } from "../ticketService";
import { unavailableAssessment } from "./mockAssessment";

// Bộ ticket giả cố định cho Board: mở trang với VITE_WORKFLOW_MOCK=true thì ai đăng nhập cũng thấy
// đúng các ticket này (cùng id, nội dung); chỉ mốc thời gian tính theo lúc mở trang để SLA luôn đủ
// trường hợp. Không phải hành vi thật: kho giả cho mọi vai thấy mọi ticket. Backend xong thì xóa
// cùng thư mục mock/.

export interface SeedTicket {
  /** Id cố định để link chi tiết còn đúng sau khi tải lại và giữa các người dùng. */
  id: string;
  title: string;
  description: string;
  creator: string;
  createdMinutesAgo: number;
  assessment?: Assessment;
  /** Kết quả AI chưa xong lúc mở trang (hiện "AI đang phân tích"). */
  assessmentPending?: boolean;
  /** Đã duyệt: bước xử lý và người giữ. */
  handle?: {
    category: Category;
    priority: Priority;
    assignee: string;
    task?: Partial<NextTaskSpec>;
  };
  /** Bước hiện tại không phải xử lý. */
  stage?: "confirm" | "closed";
  /** Chờ xác nhận: bước xác nhận mở cách đây bao lâu (mặc định 30 phút). */
  confirmMinutesAgo?: number;
  /** Đã đóng: đóng cách đây bao lâu (mặc định 30 phút) và bởi ai. */
  closedMinutesAgo?: number;
  closedBy?: "REQUESTER" | "AUTO";
}

const DUNG = "mock-requester-dung";
const HAI = "mock-requester-hai";
const AN = "mock-agent-an";
const BINH = "mock-agent-binh";
const CUONG = "mock-agent-cuong";

const HOUR = 60;
const DAY = 24 * HOUR;

/** `me` là người đang đăng nhập: giữ vài ticket để tab "Giao cho tôi" và nút Bắt đầu không trống. */
export const buildSeeds = (me: string, minutesAgo: (minutes: number) => string): SeedTicket[] => {
  const seeds: Omit<SeedTicket, "id">[] = [
    // ---------- Chờ duyệt (6) ----------
    {
      title: "Phòng thi 1304 mất mạng, thi sau 15 phút",
      description: "Cả phòng 1304 nhà A1 không vào được mạng, 15 phút nữa bắt đầu thi.",
      creator: DUNG,
      createdMinutesAgo: 3,
    },
    {
      title: "Máy bị lỗi",
      description: "Máy không dùng được, nhờ kiểm tra giúp.",
      creator: HAI,
      createdMinutesAgo: 25,
    },
    {
      title: "Điều hòa phòng A2-301 kêu to",
      description: "Điều hòa phòng A2-301 kêu rất to, ảnh hưởng giờ học.",
      creator: DUNG,
      createdMinutesAgo: 40,
      assessment: unavailableAssessment(),
    },
    {
      title: "Wi-Fi phòng họp tầng 2 yếu",
      description: "Phòng họp tầng 2 nhà B vào Wi-Fi rất chậm, họp trực tuyến hay bị giật.",
      creator: HAI,
      createdMinutesAgo: 90,
    },
    {
      title: "Không in được từ máy tính khoa",
      description: "Máy in phòng khoa báo offline, các máy khác trong khoa cũng không in được.",
      creator: DUNG,
      createdMinutesAgo: 1,
      assessmentPending: true,
    },
    {
      title: "Chuột máy 12 phòng 1303 bị đơ",
      description: "Chuột máy 12 phòng 1303 thỉnh thoảng đơ, đã thử cắm lại cổng khác.",
      creator: HAI,
      createdMinutesAgo: 200,
    },

    // ---------- Đã giao (5) ----------
    {
      title: "Máy 15 phòng 1304 không lên nguồn",
      description: "Máy số 15 phòng 1304 nhà A1 bấm nút nguồn không lên, các máy khác bình thường.",
      creator: HAI,
      createdMinutesAgo: 50,
      handle: { category: "HARDWARE", priority: "MEDIUM", assignee: me },
    },
    {
      // Quá hạn bắt đầu: HIGH phải bắt đầu trong 60 phút
      title: "Mất mạng dãy phòng B3",
      description: "Cả dãy phòng B3 mất mạng từ đầu giờ, nhiều phòng bị ảnh hưởng.",
      creator: DUNG,
      createdMinutesAgo: 150,
      handle: { category: "NETWORK", priority: "HIGH", assignee: CUONG },
    },
    {
      title: "Cài Office máy giảng viên phòng 1101",
      description: "Máy giảng viên phòng 1101 chưa có Office, cần cài trước buổi giảng tuần sau.",
      creator: HAI,
      createdMinutesAgo: 120,
      handle: { category: "SOFTWARE", priority: "LOW", assignee: BINH },
    },
    {
      // Sắp hết hạn bắt đầu (MEDIUM: 240 phút)
      title: "Không đăng nhập được hệ thống điểm",
      description: "Giảng viên báo không đăng nhập được hệ thống nhập điểm từ sáng.",
      creator: DUNG,
      createdMinutesAgo: 200,
      handle: { category: "ACCOUNT", priority: "MEDIUM", assignee: CUONG },
    },
    {
      // Ưu tiên bị nâng vì chờ lâu (R5)
      title: "Máy chiếu phòng 1201 bị mờ",
      description: "Máy chiếu phòng 1201 hình rất mờ, đã chỉnh nét nhưng không được.",
      creator: DUNG,
      createdMinutesAgo: 30 * HOUR,
      handle: {
        category: "HARDWARE",
        priority: "LOW",
        assignee: BINH,
        task: { priority: "MEDIUM", basePriority: "LOW", escalated: true },
      },
    },

    // ---------- Đang làm (4) ----------
    {
      title: "Wi-Fi tầng 7 nhà A1 chập chờn",
      description: "Cả tầng 7 nhà A1 Wi-Fi lúc được lúc không, nhiều phòng bị ảnh hưởng.",
      creator: HAI,
      createdMinutesAgo: 400,
      handle: {
        category: "NETWORK",
        priority: "HIGH",
        assignee: AN,
        task: {
          workState: "IN_PROGRESS",
          inProgressSince: minutesAgo(35),
          firstStartedAt: minutesAgo(35),
        },
      },
    },
    {
      title: "Máy in tầng 3 nhà A1 kẹt giấy liên tục",
      description: "Máy in tầng 3 nhà A1 kẹt giấy mỗi lần in hai mặt, đã gỡ giấy nhiều lần.",
      creator: DUNG,
      createdMinutesAgo: 90,
      handle: {
        category: "HARDWARE",
        priority: "HIGH",
        assignee: me,
        task: {
          workState: "IN_PROGRESS",
          inProgressSince: minutesAgo(20),
          firstStartedAt: minutesAgo(20),
        },
      },
    },
    {
      // Quá hạn xong: CRITICAL phải xong trong 120 phút
      title: "Máy chủ phòng lab mạng treo",
      description: "Máy chủ phòng lab mạng không phản hồi, cả lớp thực hành không vào được bài.",
      creator: HAI,
      createdMinutesAgo: 300,
      handle: {
        category: "NETWORK",
        priority: "CRITICAL",
        assignee: BINH,
        task: {
          workState: "IN_PROGRESS",
          inProgressSince: minutesAgo(200),
          firstStartedAt: minutesAgo(200),
        },
      },
    },
    {
      // ET lấy theo trung vị các việc tương tự
      title: "Cài lại Windows máy 21 phòng 1306",
      description: "Máy 21 phòng 1306 bị lỗi hệ điều hành, cần cài lại Windows.",
      creator: DUNG,
      createdMinutesAgo: 180,
      handle: {
        category: "SOFTWARE",
        priority: "MEDIUM",
        assignee: CUONG,
        task: {
          workState: "IN_PROGRESS",
          inProgressSince: minutesAgo(10),
          firstStartedAt: minutesAgo(10),
          estimate: { minutes: 40, basis: "SIMILAR", sampleSize: 42 },
        },
      },
    },

    // ---------- Đang chờ (4) ----------
    {
      title: "Không cài được phần mềm Matlab máy 7",
      description: "Máy 7 phòng 1302 cài Matlab báo lỗi thiếu license, cần cho buổi thực hành.",
      creator: DUNG,
      createdMinutesAgo: 180,
      handle: {
        category: "SOFTWARE",
        priority: "HIGH",
        assignee: me,
        task: {
          workState: "PENDING",
          pendingReason: "WAITING_EXTERNAL",
          pendingNote: "Chờ phòng đào tạo cấp license.",
          pendingSince: minutesAgo(60),
          workedBefore: 20,
          firstStartedAt: minutesAgo(120),
        },
      },
    },
    {
      title: "Thay ổ cứng máy 4 phòng 1305",
      description: "Ổ cứng máy 4 phòng 1305 báo lỗi SMART, máy khởi động rất chậm.",
      creator: HAI,
      createdMinutesAgo: 6 * HOUR,
      handle: {
        category: "HARDWARE",
        priority: "MEDIUM",
        assignee: AN,
        task: {
          workState: "PENDING",
          pendingReason: "WAITING_PARTS",
          pendingNote: "Chờ nhập ổ SSD mới.",
          pendingSince: minutesAgo(3 * HOUR),
          workedBefore: 25,
          firstStartedAt: minutesAgo(5 * HOUR),
        },
      },
    },
    {
      title: "Không gửi được email ra ngoài",
      description: "Email nhà trường gửi đi địa chỉ ngoài bị trả lại, nhận thư thì bình thường.",
      creator: DUNG,
      createdMinutesAgo: 8 * HOUR,
      handle: {
        category: "ACCOUNT",
        priority: "LOW",
        assignee: CUONG,
        task: {
          workState: "PENDING",
          pendingReason: "WAITING_REQUESTER",
          pendingNote: "Đã hỏi lại nội dung thư báo lỗi, chờ người gửi trả lời.",
          pendingSince: minutesAgo(2 * HOUR),
          workedBefore: 10,
          firstStartedAt: minutesAgo(6 * HOUR),
        },
      },
    },
    {
      // Chờ đã lâu: đủ ngày để có cảnh báo nhắc
      title: "Loa phòng 1202 bị rè",
      description: "Loa phòng 1202 rè khi bật to, đã thử đổi dây âm thanh.",
      creator: HAI,
      createdMinutesAgo: 6 * DAY,
      handle: {
        category: "HARDWARE",
        priority: "LOW",
        assignee: BINH,
        task: {
          workState: "PENDING",
          pendingReason: "WAITING_PARTS",
          pendingNote: "Chờ mua loa thay thế.",
          pendingSince: minutesAgo(4 * DAY),
          workedBefore: 15,
          firstStartedAt: minutesAgo(5 * DAY),
        },
      },
    },

    // ---------- Chờ xác nhận (3) ----------
    {
      title: "Máy 9 phòng 1302 không lên màn hình",
      description: "Máy 9 phòng 1302 bật nguồn có đèn nhưng màn hình không hiện gì.",
      creator: me,
      createdMinutesAgo: 120,
      handle: { category: "HARDWARE", priority: "MEDIUM", assignee: AN },
      stage: "confirm",
    },
    {
      title: "Chuột máy 3 phòng 1301 bị hỏng",
      description: "Chuột máy 3 phòng 1301 không nhận, đã thử cắm lại.",
      creator: DUNG,
      createdMinutesAgo: 5 * HOUR,
      handle: { category: "HARDWARE", priority: "LOW", assignee: BINH },
      stage: "confirm",
      confirmMinutesAgo: 90,
    },
    {
      // Sắp tự đóng nếu người gửi không xác nhận
      title: "Reset mật khẩu tài khoản thư viện",
      description: "Tôi quên mật khẩu tài khoản thư viện, nhờ đặt lại giúp.",
      creator: HAI,
      createdMinutesAgo: 3 * DAY,
      handle: { category: "ACCOUNT", priority: "LOW", assignee: CUONG },
      stage: "confirm",
      confirmMinutesAgo: 2 * DAY + 8 * HOUR,
    },

    // ---------- Mở lại (3) ----------
    {
      title: "Bàn phím máy 3 phòng 1305 liệt phím",
      description: "Bàn phím máy 3 phòng 1305 liệt mấy phím chữ, đã báo hai lần.",
      creator: HAI,
      createdMinutesAgo: 26 * HOUR,
      handle: {
        category: "HARDWARE",
        priority: "MEDIUM",
        assignee: BINH,
        task: { reopenCount: 2, workedBefore: 25 },
      },
    },
    {
      title: "Máy chiếu phòng 1203 vẫn nhấp nháy",
      description: "Máy chiếu phòng 1203 đã sửa ba lần nhưng hình vẫn nhấp nháy.",
      creator: DUNG,
      createdMinutesAgo: 4 * DAY,
      handle: {
        category: "HARDWARE",
        priority: "MEDIUM",
        assignee: AN,
        task: { reopenCount: 3, workedBefore: 50 },
      },
    },
    {
      title: "Wi-Fi phòng 1304 lại chập chờn",
      description: "Wi-Fi phòng 1304 vừa sửa xong hôm qua nhưng hôm nay lại chập chờn.",
      creator: HAI,
      createdMinutesAgo: 2 * DAY,
      handle: {
        category: "NETWORK",
        priority: "HIGH",
        assignee: me,
        task: { reopenCount: 2, workedBefore: 40 },
      },
    },

    // ---------- Đã đóng (3) ----------
    {
      title: "Quên mật khẩu tài khoản email",
      description: "Tôi quên mật khẩu email nhà trường, cần đặt lại.",
      creator: me,
      createdMinutesAgo: 50 * HOUR,
      handle: { category: "ACCOUNT", priority: "LOW", assignee: CUONG },
      stage: "closed",
      closedMinutesAgo: 2 * HOUR,
      closedBy: "REQUESTER",
    },
    {
      // Người gửi không xác nhận nên hệ thống tự đóng (R11)
      title: "Cắm lại dây mạng phòng 1306",
      description: "Dây mạng máy 5 phòng 1306 lỏng, mất mạng chập chờn.",
      creator: HAI,
      createdMinutesAgo: 5 * DAY,
      handle: { category: "NETWORK", priority: "LOW", assignee: AN },
      stage: "closed",
      closedMinutesAgo: DAY,
      closedBy: "AUTO",
    },
    {
      title: "Cấp tài khoản phòng lab cho lớp K18",
      description: "Cần tạo tài khoản phòng lab cho 45 sinh viên lớp K18.",
      creator: DUNG,
      createdMinutesAgo: 20 * DAY,
      handle: { category: "ACCOUNT", priority: "MEDIUM", assignee: CUONG },
      stage: "closed",
      closedMinutesAgo: 18 * DAY,
      closedBy: "REQUESTER",
    },
  ];

  return seeds.map((seed, index) => ({
    ...seed,
    id: `00000000-0000-4000-8000-${String(index + 1).padStart(12, "0")}`,
  }));
};
