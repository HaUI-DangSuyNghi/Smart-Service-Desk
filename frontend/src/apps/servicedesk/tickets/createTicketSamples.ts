// Bộ dữ liệu điền nhanh form tạo ticket để thử; hiện khi bật "Dữ liệu mẫu" trong Cấu hình hệ thống
import { CATEGORY_LABELS } from "./ticketLabels";
import type { Category } from "./ticketRules";

interface TicketSample {
  /** Nhãn mong đợi, chỉ để đặt tên mẫu. */
  category: Category | "UNCLEAR";
  name: string;
  values: {
    title: string;
    description: string;
    room: string;
    scope: "" | "INDIVIDUAL" | "MULTIPLE" | "WHOLE_ROOM";
    work: "" | "BLOCKED" | "WORKAROUND";
  };
}

const SAMPLES: readonly TicketSample[] = [
  {
    category: "HARDWARE",
    name: "Máy không lên nguồn (gấp)",
    values: {
      title: "Máy 15 ở phòng 1304 không lên nguồn",
      description:
        "Sáng nay bật máy số 15 ở phòng 1304 không thấy đèn nguồn, đã cắm sang ổ điện khác vẫn không lên. Em cần máy để làm bài thực hành chiều nay.",
      room: "1304",
      scope: "INDIVIDUAL",
      work: "BLOCKED",
    },
  },
  {
    category: "HARDWARE",
    name: "Máy chiếu mờ (cả phòng)",
    values: {
      title: "Máy chiếu phòng A201 bị mờ và lệch màu",
      description:
        "Máy chiếu phòng A201 hiển thị mờ, màu bị ám vàng, chữ nhỏ trên slide không đọc được. Giảng viên đang tạm dùng màn hình TV nhỏ phía trước.",
      room: "A201",
      scope: "WHOLE_ROOM",
      work: "WORKAROUND",
    },
  },
  {
    category: "HARDWARE",
    name: "Bàn phím chập chờn (nhẹ)",
    values: {
      title: "Bàn phím máy 08 ở phòng 1210 chập chờn",
      description:
        "Bàn phím máy 08 ở phòng 1210 thỉnh thoảng không nhận phím, phải rút ra cắm lại. Em đang dùng bàn phím ảo tạm.",
      room: "1210",
      scope: "INDIVIDUAL",
      work: "WORKAROUND",
    },
  },
  {
    category: "NETWORK",
    name: "Cả phòng mất mạng (gấp)",
    values: {
      title: "Cả phòng 1304 mất mạng Internet",
      description:
        "Từ 8h toàn bộ máy phòng 1304 không vào được Internet, biểu tượng mạng báo No internet access. Lớp đang thi thực hành trên hệ thống trực tuyến nên không làm tiếp được.",
      room: "1304",
      scope: "WHOLE_ROOM",
      work: "BLOCKED",
    },
  },
  {
    category: "NETWORK",
    name: "Wifi chậm (nhiều người)",
    values: {
      title: "Wifi tầng 3 nhà A rất chậm",
      description:
        "Wifi tầng 3 nhà A kết nối được nhưng tải trang rất chậm, nhiều sinh viên cùng bị từ đầu giờ chiều. Mọi người đang dùng 4G thay thế.",
      room: "",
      scope: "MULTIPLE",
      work: "WORKAROUND",
    },
  },
  {
    category: "SOFTWARE",
    name: "Không mở được Visual Studio",
    values: {
      title: "Không mở được Visual Studio trên máy 03 ở phòng 1305",
      description:
        "Mở Visual Studio trên máy 03 ở phòng 1305 thì hiện màn hình chờ rồi tự tắt, không báo lỗi. Em cần chạy bài lập trình C# trong buổi này.",
      room: "1305",
      scope: "INDIVIDUAL",
      work: "BLOCKED",
    },
  },
  {
    category: "SOFTWARE",
    name: "Thiếu MATLAB (cả phòng)",
    values: {
      title: "Phòng 1306 chưa cài MATLAB cho buổi thực hành",
      description:
        "Các máy phòng 1306 chưa có MATLAB, buổi thực hành xử lý tín hiệu sáng mai cần dùng. Cả lớp 40 sinh viên không làm bài được nếu không cài.",
      room: "1306",
      scope: "WHOLE_ROOM",
      work: "BLOCKED",
    },
  },
  {
    category: "SOFTWARE",
    name: "Office báo hết bản quyền",
    values: {
      title: "Microsoft Office báo hết bản quyền trên vài máy phòng 1210",
      description:
        "Word và Excel trên khoảng 5 máy phòng 1210 hiện thông báo sản phẩm chưa kích hoạt, chỉ xem được không sửa được. Mọi người đang dùng Google Docs tạm.",
      room: "1210",
      scope: "MULTIPLE",
      work: "WORKAROUND",
    },
  },
  {
    category: "ACCOUNT",
    name: "Không đăng nhập máy lab",
    values: {
      title: "Không đăng nhập được tài khoản sinh viên vào máy phòng 1304",
      description:
        "Em nhập đúng mã sinh viên và mật khẩu nhưng máy phòng 1304 báo tài khoản hoặc mật khẩu không đúng. Hôm qua vẫn đăng nhập được bình thường.",
      room: "1304",
      scope: "INDIVIDUAL",
      work: "BLOCKED",
    },
  },
  {
    category: "ACCOUNT",
    name: "Quên mật khẩu email",
    values: {
      title: "Quên mật khẩu email trường",
      description:
        "Em quên mật khẩu email sinh viên, chức năng khôi phục gửi mã về số điện thoại cũ em không còn dùng. Nhờ hỗ trợ đặt lại mật khẩu.",
      room: "",
      scope: "INDIVIDUAL",
      work: "WORKAROUND",
    },
  },
  {
    category: "OTHER",
    name: "Điều hoà hỏng",
    values: {
      title: "Điều hoà phòng 1304 không hoạt động",
      description:
        "Điều hoà phòng 1304 bật không chạy, phòng rất nóng, các máy tính cũng nóng hơn bình thường. Lớp đang mở cửa sổ để học tạm.",
      room: "1304",
      scope: "WHOLE_ROOM",
      work: "WORKAROUND",
    },
  },
  {
    category: "OTHER",
    name: "Mượn thêm ghế",
    values: {
      title: "Cần mượn thêm 10 ghế cho phòng A201",
      description:
        "Buổi seminar chiều thứ 6 ở phòng A201 có khoảng 60 người, phòng chỉ có 50 ghế. Nhờ hỗ trợ mượn thêm 10 ghế.",
      room: "A201",
      scope: "",
      work: "",
    },
  },
  {
    category: "UNCLEAR",
    name: "Mô tả mơ hồ (thiếu thông tin)",
    values: {
      title: "Máy bị lỗi",
      description: "Máy không dùng được.",
      room: "",
      scope: "",
      work: "",
    },
  },
];

const groupLabel = (category: TicketSample["category"]) =>
  category === "UNCLEAR" ? "Không rõ" : CATEGORY_LABELS[category];

export const SAMPLE_OPTIONS = SAMPLES.map((sample, index) => ({
  value: String(index),
  label: `${groupLabel(sample.category)} · ${sample.name}`,
}));

// Number("") là 0: chọn lại ô trống thì không điền mẫu đầu tiên
export const sampleValues = (value: string) => (value ? SAMPLES[Number(value)]?.values : undefined);
