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

const Screenshot = ({
  className,
  src,
  alt,
  priority = false,
}: {
  className?: string;
  src: string;
  alt: string;
  priority?: boolean;
}) => {
  return (
    <div className={className}>
      <img
        src={src}
        alt={alt}
        width={1912}
        height={867}
        loading={priority ? "eager" : "lazy"}
        fetchPriority={priority ? "high" : "auto"}
        decoding="async"
        className="block h-auto w-full rounded-2xl border border-(--landing-border) shadow-[0_8px_40px_rgba(82,123,20,0.12)]"
      />
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
      <div className="absolute inset-0 bg-black/70" />
      <Particles />

      <div className="relative mx-auto max-w-295">
        <Reveal className="mb-8 inline-flex max-w-full flex-wrap items-center justify-center gap-2 rounded-full border border-white/20 bg-white/5 px-4 py-2.5 text-xs font-bold text-white/70 backdrop-blur-sm sm:px-5 sm:text-sm">
          <span className="animate-pulse-dot size-2 rounded-full bg-(--landing-lime)" />
          <span className="text-(--landing-lime)">Smart Service Desk</span>
          Hỗ trợ CNTT · Đặt phòng · Tri thức nội bộ
        </Reveal>

        <Reveal
          as="h1"
          delay={60}
          className="mx-auto mb-6 max-w-265 text-[clamp(38px,5.2vw,70px)] leading-[1.06] font-black tracking-tight text-white"
        >
          Hỗ trợ CNTT tập trung
          <br />
          Theo dõi đến khi hoàn tất.
        </Reveal>

        <Reveal
          delay={120}
          className="mx-auto mb-10 max-w-215 text-lg leading-relaxed text-white/70"
        >
          Gửi yêu cầu, tiếp nhận, duyệt và giao việc trong cùng một hệ thống. Người gửi biết tiến
          độ, kỹ thuật viên biết việc cần làm, điều phối viên theo dõi được toàn bộ luồng xử lý. AI
          hỗ trợ phân loại khi được bật, điều phối viên quyết định kết quả duyệt.
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
            Vào ứng dụng
            <ArrowRight className="size-4" />
          </Link>
        </Reveal>

        <Reveal delay={240} className="relative mx-auto max-w-250">
          <Screenshot
            className="relative z-2 mx-auto w-[90%]"
            src="/landingpage1.png"
            alt="Trang chủ Smart Service Desk với truy cập nhanh, kho tri thức và công việc"
            priority
          />
          <div className="animate-float absolute bottom-[-8%] left-[-3%] z-3 w-[22%]">
            <Screenshot src="/landingpage2.png" alt="Lịch ca trực của kỹ thuật viên" />
          </div>
          <div className="animate-float absolute top-[6%] right-[-3%] z-3 w-[26%] [animation-delay:1.5s]">
            <Screenshot
              src="/landingpage3.png"
              alt="Danh sách công việc tiếp nhận, duyệt và giao"
            />
          </div>
          <div className="animate-float absolute right-[-2%] bottom-[-5%] z-3 w-[22%] [animation-delay:3s]">
            <Screenshot src="/landingpage4.png" alt="Thông báo về các thay đổi của yêu cầu" />
          </div>
        </Reveal>

        <div id="stack">
          <Reveal delay={300} className="mt-24">
            <p className="mb-6 text-center text-[11px] font-semibold tracking-widest text-white/50 uppercase">
              Công nghệ nền tảng
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
