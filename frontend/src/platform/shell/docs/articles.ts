export const DOC_GROUPS = [
  { id: "start", title: "Bắt đầu" },
  { id: "roles", title: "Theo vai trò" },
  { id: "features", title: "Sử dụng hệ thống" },
  { id: "admin", title: "Quản trị & cài đặt" },
  { id: "help", title: "Trợ giúp" },
] as const;

export const DOCS = [
  {
    id: "gioi-thieu",
    title: "Giới thiệu",
    group: "start",
    description: "Tổng quan nền tảng, các chức năng và vai trò của AI.",
  },
  {
    id: "bat-dau",
    title: "Bắt đầu nhanh",
    group: "start",
    description: "Đăng nhập, chọn workspace và thực hiện thao tác đầu tiên.",
  },
  {
    id: "tai-khoan",
    title: "Tài khoản & phân quyền",
    group: "start",
    description: "Hiểu workspace, vai trò và quyền thao tác của bạn.",
  },
  {
    id: "giao-dien",
    title: "Làm quen giao diện",
    group: "start",
    description: "Tìm đúng chức năng trên máy tính và điện thoại.",
  },
  {
    id: "nguoi-yeu-cau",
    title: "Người yêu cầu",
    group: "roles",
    description: "Gửi ticket, bổ sung thông tin và xác nhận kết quả xử lý.",
  },
  {
    id: "ky-thuat-vien",
    title: "Kỹ thuật viên",
    group: "roles",
    description: "Theo dõi lịch công việc, xử lý, bàn giao và đăng ký ca trực.",
  },
  {
    id: "dieu-phoi-vien",
    title: "Điều phối viên",
    group: "roles",
    description: "Tiếp nhận, duyệt, phân công và theo dõi công việc của đơn vị.",
  },
  {
    id: "trang-thai",
    title: "Trạng thái & hạn xử lý",
    group: "features",
    description: "Phân biệt trạng thái ticket, ưu tiên, ước tính và SLA.",
  },
  {
    id: "tai-lieu",
    title: "Kho tài liệu",
    group: "features",
    description: "Tìm kiếm, đọc, tải lên và chọn phạm vi xem tài liệu.",
  },
  {
    id: "hoi-dap",
    title: "Hỏi đáp tài liệu",
    group: "features",
    description: "Đặt câu hỏi với AI và kiểm tra câu trả lời qua trích dẫn.",
  },
  {
    id: "dat-phong",
    title: "Yêu cầu đặt phòng",
    group: "features",
    description: "Gửi tên phòng và theo dõi quy trình duyệt yêu cầu.",
  },
  {
    id: "theo-doi",
    title: "Thông báo & theo dõi",
    group: "features",
    description: "Đọc thông báo, Dashboard và nhật ký hoạt động.",
  },
  {
    id: "quan-tri",
    title: "Hướng dẫn quản trị",
    group: "admin",
    description: "Quản lý tài khoản, phân quyền và cấu hình hệ thống.",
  },
  {
    id: "cai-dat",
    title: "Tự cài đặt",
    group: "admin",
    description: "Chuẩn bị môi trường, chọn chế độ AI và khởi động dự án.",
  },
  {
    id: "su-co",
    title: "Xử lý sự cố",
    group: "help",
    description: "Tra cứu cách xử lý lỗi đăng nhập, Docker, tài liệu và AI.",
  },
  {
    id: "cau-hoi",
    title: "Câu hỏi thường gặp",
    group: "help",
    description: "Giải đáp những tình huống thường gặp khi sử dụng.",
  },
  {
    id: "thuat-ngu",
    title: "Thuật ngữ",
    group: "help",
    description: "Giải thích các khái niệm và từ viết tắt trong hệ thống.",
  },
  {
    id: "dong-gop",
    title: "Hỗ trợ & đóng góp",
    group: "help",
    description: "Nhận hỗ trợ, đóng góp cho dự án và tìm hiểu giấy phép.",
  },
] as const;

export type DocArticle = (typeof DOCS)[number];
