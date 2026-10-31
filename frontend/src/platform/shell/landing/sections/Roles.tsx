import { Reveal } from "../Reveal";

const ROLES = [
  {
    tag: "Requester",
    title: "Người yêu cầu",
    items: [
      "Gửi ticket hỗ trợ CNTT và yêu cầu đặt phòng",
      "Theo dõi, trao đổi và sửa hoặc hủy khi còn được phép",
      "Xác nhận kết quả ticket hoặc yêu cầu xử lý lại",
    ],
  },
  {
    tag: "Agent",
    title: "Kỹ thuật viên",
    items: [
      "Xem công việc được giao theo danh sách hoặc ngày",
      "Bắt đầu, tạm dừng, tiếp tục và báo xong công việc",
      "Đăng ký ca trực, tra cứu tài liệu và cập nhật tiến độ",
    ],
  },
  {
    tag: "Coordinator",
    title: "Điều phối viên",
    items: [
      "Tiếp nhận thông tin, duyệt và giao ticket",
      "Điều phối người xử lý, ca trực và yêu cầu đặt phòng",
      "Theo dõi tồn đọng, quá hạn và thống kê trong workspace",
    ],
  },
];

export const Roles = () => {
  return (
    <section
      id="roles"
      className="border-t border-(--landing-border) bg-(--landing-bg-soft) px-6 py-20 lg:px-8"
    >
      <div className="mx-auto max-w-360">
        <Reveal className="mb-14 max-w-190">
          <div className="mb-3.5 text-sm font-bold tracking-[0.08em] text-(--landing-accent) uppercase">
            Phân quyền
          </div>
          <h2 className="mb-3.5 text-[clamp(34px,4vw,50px)] leading-tight font-extrabold tracking-tight text-(--landing-text)">
            Đúng việc cho từng vai trò
          </h2>
          <p className="text-xl leading-relaxed text-(--landing-text-dim)">
            Người gửi theo dõi yêu cầu, kỹ thuật viên tập trung xử lý, điều phối viên tổ chức công
            việc. Giao diện và quyền thao tác được xác định theo vai trò trong workspace đang chọn.
          </p>
        </Reveal>

        <div className="grid grid-cols-1 gap-6 md:grid-cols-3">
          {ROLES.map((role, i) => (
            <Reveal key={role.tag} delay={i * 80}>
              <div className="h-full rounded-[20px] border border-(--landing-border) bg-white p-6 transition-all lg:p-8 duration-300 hover:-translate-y-1 hover:shadow-[0_4px_24px_rgba(0,0,0,0.1)]">
                <span className="mb-2.5 inline-block text-xs font-bold tracking-[0.04em] text-(--landing-accent) uppercase">
                  {role.tag}
                </span>
                <h3 className="text-xl font-bold tracking-tight text-(--landing-text)">
                  {role.title}
                </h3>
                <ul className="mt-3.5 list-disc pl-4.5 text-base text-(--landing-text-dim)">
                  {role.items.map((item) => (
                    <li key={item} className="mb-1.5">
                      {item}
                    </li>
                  ))}
                </ul>
              </div>
            </Reveal>
          ))}
        </div>
      </div>
    </section>
  );
};
