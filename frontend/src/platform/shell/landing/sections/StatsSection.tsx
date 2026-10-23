import { CheckCircle2, Eye, TrendingUp } from "lucide-react";
import { Reveal } from "../Reveal";

const FEATURES = [
  {
    icon: CheckCircle2,
    title: "Kiểm thử với hạ tầng thật",
    desc: "Nghiệm thu chạy qua Keycloak, APISIX và PostgreSQL thật, gồm cả các ca lỗi, tranh chấp và khôi phục dữ liệu.",
  },
  {
    icon: Eye,
    title: "Truy vết từng yêu cầu",
    desc: "Mỗi request mang X-Request-Id xuyên qua gateway, backend và nhật ký audit — dễ tra soát khi có sự cố.",
  },
  {
    icon: TrendingUp,
    title: "Mở rộng không phá vỡ",
    desc: "Ranh giới ba tầng được kiểm tự động mỗi lần build; thêm ứng dụng mới không phải sửa nền tảng.",
  },
];

const STATS = [
  ["3", "tầng kiến trúc DX-OS"],
  ["4", "năng lực H-P-D-I"],
  ["2", "workspace cô lập dữ liệu"],
  ["3", "vai trò nghiệp vụ"],
];

export const StatsSection = () => {
  return (
    <section className="bg-(--landing-bg-soft) px-6 py-16 lg:px-8">
      <Reveal className="mx-auto max-w-360 overflow-hidden rounded-[28px] border border-(--landing-border) bg-white p-8 sm:p-12">
        <div className="grid items-center gap-12 lg:grid-cols-2">
          <div className="flex aspect-4/3 items-center justify-center rounded-2xl border border-(--landing-border) bg-linear-to-br from-(--landing-accent-soft) to-(--landing-lime-soft)">
            <span className="text-xs font-semibold tracking-wide text-(--landing-text-dim) uppercase">
              Ảnh minh hoạ Dashboard
            </span>
          </div>
          <div>
            <h3 className="mb-8 text-[clamp(34px,4vw,50px)] leading-[1.07] font-extrabold tracking-tight text-(--landing-text)">
              Nền tảng vững chắc, đã kiểm chứng
            </h3>
            <div className="flex flex-col gap-7">
              {FEATURES.map((f) => (
                <div key={f.title} className="flex items-start gap-4">
                  <div className="mt-1 grid size-12 shrink-0 place-items-center rounded-xl bg-(--landing-text)">
                    <f.icon className="size-6 text-white" />
                  </div>
                  <div>
                    <h4 className="mb-1.5 text-base font-bold tracking-wide text-(--landing-text) uppercase">
                      {f.title}
                    </h4>
                    <p className="text-base leading-relaxed text-(--landing-text-dim)">{f.desc}</p>
                  </div>
                </div>
              ))}
            </div>
          </div>
        </div>

        <div className="mt-10 grid grid-cols-2 divide-x divide-(--landing-border) border-t border-(--landing-border) pt-8 sm:grid-cols-4">
          {STATS.map(([value, label]) => (
            <div key={label} className="px-4 text-center">
              <div className="mb-1.5 text-[clamp(28px,3vw,42px)] leading-none font-black tracking-tight text-(--landing-text)">
                {value}
              </div>
              <div className="text-[13px] font-medium text-(--landing-text-dim)">{label}</div>
            </div>
          ))}
        </div>
      </Reveal>
    </section>
  );
};
