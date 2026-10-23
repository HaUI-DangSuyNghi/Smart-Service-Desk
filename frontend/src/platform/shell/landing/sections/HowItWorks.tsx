import { AppWindow, Blocks, Server } from "lucide-react";
import { Reveal } from "../Reveal";

const PILLARS = [
  {
    icon: Server,
    title: "Tầng 1 — Open-Core",
    desc: "Nền tảng headless dùng chung: Keycloak cho định danh, APISIX làm cổng API, Flowable chạy quy trình, PostgreSQL lưu dữ liệu và nhật ký audit. Không có giao diện, mọi thứ qua API.",
  },
  {
    icon: Blocks,
    title: "Tầng 2 — H-P-D-I",
    desc: "Không gian năng lực số dùng chung cho mọi ứng dụng: Human (người dùng, workspace), Process (hộp việc, timeline), Data (tri thức, thống kê), Intelligence (AI gợi ý).",
  },
  {
    icon: AppWindow,
    title: "Tầng 3 — Ứng dụng",
    desc: "Smart Service Desk chỉ viết phần nghiệp vụ riêng: ticket, quy trình hỗ trợ, thống kê. Phần còn lại dùng lại từ hai tầng dưới — ứng dụng khác cũng cắm vào được như vậy.",
  },
];

export const HowItWorks = () => {
  return (
    <section id="how" className="bg-(--landing-bg-soft) px-6 py-20 lg:px-8">
      <div className="mx-auto max-w-360">
        <Reveal className="mx-auto mb-14 max-w-250 text-center">
          <h2 className="mb-5 text-[clamp(34px,4.5vw,58px)] leading-[1.1] font-extrabold tracking-tight text-(--landing-text)">
            Ba Tầng, Một Nền Tảng Dùng Chung
          </h2>
          <p className="text-xl leading-relaxed text-(--landing-text-dim)">
            Theo kiến trúc DX-OS: dịch vụ lõi chạy ngầm, năng lực số dùng chung ở giữa, và ứng dụng
            nghiệp vụ ở trên cùng — thay ứng dụng không phải sửa nền tảng.
          </p>
        </Reveal>

        <div className="grid grid-cols-1 gap-6 sm:grid-cols-3">
          {PILLARS.map((p, i) => (
            <Reveal key={p.title} delay={i * 80}>
              <div className="h-full rounded-[20px] border border-(--landing-border) bg-[#f9f9fa] p-10 pb-8 transition-all duration-300 hover:-translate-y-1 hover:shadow-[0_4px_24px_rgba(0,0,0,0.14)]">
                <div className="mb-5 grid size-12 place-items-center rounded-xl border border-(--landing-border) bg-white text-(--landing-text)">
                  <p.icon className="size-6" />
                </div>
                <h3 className="mb-2.5 text-xl font-bold text-(--landing-text)">{p.title}</h3>
                <p className="text-base leading-relaxed text-(--landing-text-dim)">{p.desc}</p>
              </div>
            </Reveal>
          ))}
        </div>
      </div>
    </section>
  );
};
