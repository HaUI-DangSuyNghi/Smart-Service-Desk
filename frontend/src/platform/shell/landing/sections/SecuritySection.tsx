import { Gauge, KeyRound, ScrollText } from "lucide-react";
import { Reveal } from "../Reveal";

const ITEMS = [
  {
    icon: KeyRound,
    title: "Đăng nhập SSO, quyền ở backend",
    desc: "Đăng nhập qua Keycloak bằng Authorization Code + PKCE. Backend tự kiểm token, workspace và vai trò cho mọi request — không tin dữ liệu client gửi lên.",
  },
  {
    icon: Gauge,
    title: "Giới hạn ngay tại cổng API",
    desc: "APISIX là lối vào duy nhất: giới hạn tần suất request và chặn body quá lớn trước khi tới backend.",
  },
  {
    icon: ScrollText,
    title: "Audit không tách rời dữ liệu",
    desc: "Bản ghi audit ghi cùng transaction với dữ liệu nghiệp vụ: ai, làm gì, lúc nào, kèm request id.",
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
              Bảo mật &amp; Cô lập
              <br />
              Theo Workspace
            </h3>
            <p className="text-base leading-relaxed text-(--landing-text-dim)">
              Mỗi đơn vị là một workspace riêng: người ngoài workspace không đọc được ticket, tài
              liệu hay gợi ý AI của workspace khác.
            </p>
          </div>

          <div className="grid grid-cols-1 gap-8 sm:grid-cols-3">
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
