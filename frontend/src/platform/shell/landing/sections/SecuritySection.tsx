import { Gauge, KeyRound, ScrollText } from "lucide-react";
import { Reveal } from "../Reveal";

const ITEMS = [
  {
    icon: KeyRound,
    title: "Đăng nhập và xác định vai trò",
    desc: "Tài khoản được quản lý tập trung qua Keycloak. Quyền truy cập gắn với từng workspace và được kiểm tra khi thực hiện thao tác.",
  },
  {
    icon: Gauge,
    title: "Truy cập qua cổng API",
    desc: "Các yêu cầu tới dịch vụ đi qua APISIX để định tuyến và áp dụng giới hạn truy cập theo cấu hình triển khai.",
  },
  {
    icon: ScrollText,
    title: "Ghi nhận thay đổi nghiệp vụ",
    desc: "Nhật ký lưu người thực hiện, hành động và thời điểm; lịch sử yêu cầu giúp đối chiếu các lần sửa, hủy và đóng.",
  },
];

export const SecuritySection = () => {
  return (
    <section className="bg-(--landing-bg-soft) px-6 py-16 lg:px-8">
      <Reveal className="relative mx-auto max-w-360 overflow-hidden rounded-[28px] border border-(--landing-border) bg-white p-8 sm:p-12">
        <div className="absolute inset-0 bg-[radial-gradient(ellipse_at_70%_50%,var(--landing-lime-soft)_0%,transparent_60%)]" />
        <div className="relative">
          <div className="mb-12 max-w-190">
            <h3 className="mb-4 text-[clamp(34px,4vw,50px)] leading-[1.07] font-extrabold tracking-tight text-(--landing-text)">
              Truy cập theo vai trò,
              <br />
              dữ liệu theo workspace
            </h3>
            <p className="text-base leading-relaxed text-(--landing-text-dim)">
              Chọn workspace để làm việc trong đúng phạm vi của đơn vị. Ticket, tài liệu, công việc
              và các thao tác được hiển thị theo quyền của tài khoản tại workspace đó.
            </p>
          </div>

          <div className="grid grid-cols-1 gap-8 md:grid-cols-3">
            {ITEMS.map((item) => (
              <div key={item.title}>
                <div className="mb-3.5 grid size-12 place-items-center rounded-xl bg-(--landing-text)">
                  <item.icon className="size-6 text-white" />
                </div>
                <h4 className="mb-2 text-lg font-bold text-(--landing-text)">{item.title}</h4>
                <p className="text-base leading-relaxed text-(--landing-text-dim)">{item.desc}</p>
              </div>
            ))}
          </div>
        </div>
      </Reveal>
    </section>
  );
};
