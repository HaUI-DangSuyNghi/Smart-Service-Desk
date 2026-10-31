import { Link } from "react-router";
import { ArrowRight, Play } from "lucide-react";
import { APP_HOME } from "../links";
import { Reveal } from "../Reveal";

const STACK = [
  "Java 17",
  "Spring Boot 3.3",
  "Flowable 7.1",
  "Keycloak 26",
  "Apache APISIX 3.11",
  "PostgreSQL 16",
  "Python Intelligence",
  "React 19 + Vite",
  "Tailwind CSS v4",
  "shadcn/ui",
  "Docker Compose",
];

// Sinh 1 lần khi nạp module, không gọi Math.random() mỗi lần render
const PARTICLES = Array.from({ length: 18 }, (_, i) => ({
  id: i,
  size: Math.random() * 4 + 2,
  left: Math.random() * 100,
  duration: Math.random() * 15 + 10,
  delay: Math.random() * -20,
  color: ["rgba(82,123,20,0.35)", "rgba(135,202,37,0.35)"][i % 2],
}));

const Particles = () => {
  return (
    <div className="pointer-events-none absolute inset-0 overflow-hidden">
      {PARTICLES.map((p) => (
        <span
          key={p.id}
          className="absolute rounded-full"
          style={{
            width: p.size,
            height: p.size,
            left: `${p.left}%`,
            bottom: 0,
            background: p.color,
            animation: `float-particle ${p.duration}s linear infinite`,
            animationDelay: `${p.delay}s`,
          }}
        />
      ))}
    </div>
  );
};

const ScreenshotPlaceholder = ({ className, label }: { className?: string; label: string }) => {
  return (
    <div className={className}>
      <div className="relative flex aspect-16/10 items-center justify-center overflow-hidden rounded-2xl border border-(--landing-border) bg-linear-to-br from-(--landing-accent-soft) to-(--landing-lime-soft) shadow-[0_8px_40px_rgba(82,123,20,0.12)]">
        <span className="text-xs font-semibold tracking-wide text-(--landing-text-dim) uppercase">
          {label}
        </span>
      </div>
    </div>
  );
};

export const Hero = () => {
  return (
    <section
      id="top"
      className="relative overflow-hidden bg-cover bg-center px-5 pt-28 pb-24 text-center sm:pt-32"
      style={{ backgroundImage: "url('/landing/banner-phong-may.jpg')" }}
    >
      <div className="absolute inset-0 bg-black/60" />
      <Particles />

      <div className="relative mx-auto max-w-295">
        <Reveal className="mb-8 inline-flex max-w-full flex-wrap items-center justify-center gap-2 rounded-full border border-white/20 bg-white/5 px-4 py-2.5 text-xs font-bold text-white/70 backdrop-blur-sm sm:px-5 sm:text-sm">
          <span className="animate-pulse-dot size-2 rounded-full bg-(--landing-lime)" />
          <span className="text-(--landing-lime)">DX-OS</span>
          Open-Core · H-P-D-I · Ứng dụng nghiệp vụ
        </Reveal>

        <Reveal
          as="h1"
          delay={60}
          className="mx-auto mb-6 max-w-265 text-[clamp(38px,5.2vw,70px)] leading-[1.06] font-black tracking-tight text-white"
        >
          Hỗ trợ CNTT thông minh
          <br />
          với AI gợi ý, con người quyết định
        </Reveal>

        <Reveal
          delay={120}
          className="mx-auto mb-10 max-w-215 text-lg leading-relaxed text-white/70"
        >
          Smart Service Desk số hoá yêu cầu hỗ trợ CNTT trong trường đại học: ticket chạy theo quy
          trình BPMN có lịch sử đầy đủ, AI đề xuất phân loại kèm lý do, và mọi dịch vụ lõi — định
          danh, cổng API, quy trình, dữ liệu — dùng chung từ nền tảng Open-Core.
        </Reveal>

        <Reveal delay={180} className="mb-20 flex flex-wrap justify-center gap-3.5">
          <a
            href="#how"
            className="inline-flex items-center gap-2 rounded-[10px] border-[1.5px] border-white bg-white px-7 py-3.5 text-base font-bold text-(--landing-text) transition-all hover:-translate-y-0.5 hover:bg-gray-50"
          >
            <Play className="size-4" />
            Xem cách hoạt động
          </a>
          <Link
            to={APP_HOME}
            className="inline-flex items-center gap-2 rounded-[10px] bg-(--landing-accent) px-7 py-3.5 text-base font-bold text-white transition-all hover:-translate-y-0.5 hover:bg-(--landing-accent-hover)"
          >
            Dùng thử demo
            <ArrowRight className="size-4" />
          </Link>
        </Reveal>

        <Reveal delay={240} className="relative mx-auto max-w-250">
          <ScreenshotPlaceholder
            className="relative z-2 mx-auto w-[90%]"
            label="Hộp việc (Inbox)"
          />
          <div className="animate-float absolute bottom-[-8%] left-[-3%] z-3 w-[22%]">
            <ScreenshotPlaceholder label="Chi tiết ticket" />
          </div>
          <div className="animate-float absolute top-[6%] right-[-3%] z-3 w-[26%] [animation-delay:1.5s]">
            <ScreenshotPlaceholder label="AI Triage" />
          </div>
          <div className="animate-float absolute right-[-2%] bottom-[-5%] z-3 w-[22%] [animation-delay:3s]">
            <ScreenshotPlaceholder label="Timeline quy trình" />
          </div>
        </Reveal>

        <div id="stack">
          <Reveal delay={300} className="mt-24">
            <p className="mb-6 text-center text-[11px] font-semibold tracking-widest text-white/50 uppercase">
              Được xây trên phần mềm nguồn mở
            </p>
            <div className="marquee-mask relative overflow-hidden">
              <div className="marquee-track animate-marquee flex w-max items-center gap-16">
                {[...STACK, ...STACK].map((item, i) => (
                  <span
                    key={i}
                    className="text-2xl font-extrabold tracking-tight whitespace-nowrap text-white/50 transition-all hover:scale-110 hover:text-white"
                  >
                    {item}
                  </span>
                ))}
              </div>
            </div>
          </Reveal>
        </div>
      </div>
    </section>
  );
};
