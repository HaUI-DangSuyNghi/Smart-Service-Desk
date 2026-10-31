import { useEffect, useRef } from "react";
import {
  ArrowRight,
  BookOpen,
  Brain,
  GitBranch,
  Hand,
  LifeBuoy,
  ListChecks,
  RefreshCw,
  ScrollText,
  ShieldCheck,
} from "lucide-react";
import { Reveal } from "../Reveal";

const SERVICES = [
  {
    title: "Yêu cầu có người phụ trách",
    image: "/process.png",
    imageAlt: "Bảng ticket theo các bước tiếp nhận, chờ duyệt, đã giao và đang làm",
    imageWidth: 1917,
    imageHeight: 867,
    desc: "Tập trung ticket CNTT và yêu cầu đặt phòng vào cùng một nơi. Mỗi yêu cầu có trạng thái, người tham gia và tiến độ để người gửi biết việc của mình đang được xử lý đến đâu.",
    linkLabel: "Xem vai trò xử lý",
    features: [
      {
        icon: GitBranch,
        title: "Tiếp nhận, duyệt và giao việc",
        desc: "Điều phối viên kiểm tra thông tin và phân công; kỹ thuật viên xem công việc theo danh sách hoặc theo ngày.",
      },
      {
        icon: Hand,
        title: "Cập nhật ngay trong quá trình xử lý",
        desc: "Bắt đầu, tạm dừng, tiếp tục hoặc báo xong. Thông báo trong ứng dụng và email giúp các bên theo dõi những thay đổi quan trọng.",
      },
      {
        icon: RefreshCw,
        title: "Người gửi theo dõi kết quả",
        desc: "Sửa hoặc hủy khi yêu cầu còn được phép thay đổi. Với ticket đã báo xong, người gửi có thể xác nhận hoặc yêu cầu xử lý lại.",
      },
    ],
  },
  {
    title: "AI hỗ trợ bước phân loại",
    image: "/filter.png",
    imageAlt: "Giao diện phân loại và lọc thông tin ticket hỗ trợ CNTT",
    imageWidth: 1916,
    imageHeight: 870,
    desc: "Khi được bật và cấu hình, AI phân tích nội dung ticket để gợi ý loại sự cố, mức ưu tiên và hướng xử lý. Điều phối viên xem lại đề xuất trước khi duyệt và giao việc.",
    linkLabel: "Xem vai trò điều phối",
    features: [
      {
        icon: Brain,
        title: "Có lý do để đối chiếu",
        desc: "Xem đề xuất cùng phần giải thích, thông tin làm căn cứ và dấu hiệu cần xem lại ngay trong chi tiết ticket.",
      },
      {
        icon: ListChecks,
        title: "Duyệt trước khi giao",
        desc: "Điều phối viên có thể điều chỉnh phân loại, ưu tiên và người xử lý theo tình huống thực tế.",
      },
      {
        icon: ShieldCheck,
        title: "Chọn cách vận hành phù hợp",
        desc: "Có thể triển khai với AI trên máy riêng, dịch vụ API hoặc tắt AI để tiếp nhận và phân loại thủ công.",
      },
    ],
  },
  {
    title: "Tri thức và số liệu trong tầm tay",
    image: "/chatRAG.png",
    imageAlt: "Giao diện hỏi đáp tài liệu nội bộ trên Smart Service Desk",
    imageWidth: 1918,
    imageHeight: 866,
    desc: "Tra cứu tài liệu nội bộ, xem lịch sử yêu cầu và theo dõi tình hình xử lý trên Dashboard. Thông tin hiển thị theo quyền của người dùng trong workspace.",
    linkLabel: "Xem phạm vi truy cập",
    features: [
      {
        icon: BookOpen,
        title: "Kho tri thức nội bộ",
        desc: "Tập hợp tài liệu hướng dẫn, tìm kiếm và hỏi đáp tài liệu khi tính năng AI tương ứng được cấu hình.",
      },
      {
        icon: ScrollText,
        title: "Lịch sử để tra cứu",
        desc: "Theo dõi các bước xử lý, người thực hiện và lý do thay đổi; nhật ký hỗ trợ đối chiếu khi cần.",
      },
      {
        icon: LifeBuoy,
        title: "Dashboard theo khoảng ngày",
        desc: "Người có quyền xem được số yêu cầu mới, đang xử lý, đã xong, chờ nhận và quá hạn trong khoảng thời gian đã chọn.",
      },
    ],
  },
];

const NAVBAR_H = 64; // = h-16 của LandingHeader
// Khớp lg: của Tailwind. Dưới mốc này panel 1 cột cao hơn màn hình, xếp chồng sẽ bị cắt mất phần dưới
const STACK_BREAKPOINT = 1024;

const easeInOutCubic = (t: number) => {
  return t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
};

const clamp = (v: number, min: number, max: number) => {
  return Math.min(Math.max(v, min), max);
};

export const ServicesShowcase = () => {
  const scrollAreaRef = useRef<HTMLDivElement>(null);
  const stickyWrapRef = useRef<HTMLDivElement>(null);
  const panelRefs = useRef<(HTMLDivElement | null)[]>([]);

  useEffect(() => {
    const scrollArea = scrollAreaRef.current;
    const stickyWrap = stickyWrapRef.current;
    const panels = panelRefs.current.filter((p): p is HTMLDivElement => p !== null);
    if (!scrollArea || !stickyWrap) return;
    const N = panels.length;
    // "<" chứ không "<=": lg: của Tailwind bắt đầu từ đúng 1024px
    const isMobile = () => window.innerWidth < STACK_BREAKPOINT;

    let raf: number | null = null;
    let resizeTimer: ReturnType<typeof setTimeout> | undefined;
    let cancelled = false;

    // Đo panel cao nhất, đẩy panel 2..N xuống dưới, đặt chiều cao vùng cuộn = N panel
    const setup = () => {
      if (!scrollArea || !stickyWrap) return;
      if (isMobile()) {
        scrollArea.style.height = "auto";
        stickyWrap.style.height = "auto";
        panels.forEach((p) => (p.style.transform = ""));
        return;
      }
      const panelH = panels.reduce((max, p) => {
        p.style.transform = "translateY(0)";
        return Math.max(max, p.offsetHeight);
      }, 0);
      for (let i = 1; i < N; i++) panels[i]!.style.transform = "translateY(100%)";
      stickyWrap.style.height = `${panelH}px`;
      scrollArea.style.height = `${panelH * N}px`;
    };

    // Mỗi lần cuộn: tính tiến độ [0,1] trong vùng cuộn, chia đều cho từng panel trượt lên
    const onScroll = () => {
      if (!scrollArea || !stickyWrap || isMobile()) return;
      const areaTop = scrollArea.getBoundingClientRect().top + window.scrollY;
      const scrollStart = areaTop - NAVBAR_H;
      const scrollEnd = areaTop + scrollArea.offsetHeight - stickyWrap.offsetHeight - NAVBAR_H;
      const scrollRange = scrollEnd - scrollStart;
      if (scrollRange <= 0) return;

      const progress = clamp((window.scrollY - scrollStart) / scrollRange, 0, 1);
      for (let i = 1; i < N; i++) {
        const panelStart = (i - 1) / (N - 1);
        const panelEnd = i / (N - 1);
        const localT = clamp((progress - panelStart) / (panelEnd - panelStart), 0, 1);
        panels[i]!.style.transform = `translateY(${(1 - easeInOutCubic(localT)) * 100}%)`;
      }
    };

    const tick = () => {
      onScroll();
      raf = requestAnimationFrame(tick);
    };

    const start = () => {
      if (cancelled) return;
      setup();
      raf = requestAnimationFrame(tick);
    };

    if (document.fonts?.ready) {
      document.fonts.ready.then(start);
    } else {
      setTimeout(start, 300);
    }

    const onResize = () => {
      clearTimeout(resizeTimer);
      resizeTimer = setTimeout(setup, 150);
    };
    window.addEventListener("resize", onResize, { passive: true });

    return () => {
      cancelled = true;
      if (raf) cancelAnimationFrame(raf);
      clearTimeout(resizeTimer);
      window.removeEventListener("resize", onResize);
    };
  }, []);

  return (
    <section id="services" className="border-t border-(--landing-border) bg-white pt-20">
      <Reveal className="mx-auto mb-14 grid max-w-360 items-end gap-12 px-6 lg:grid-cols-[3fr_2fr] lg:px-14">
        <h2 className="text-[clamp(32px,4.5vw,62px)] leading-[1.07] font-extrabold tracking-tight text-(--landing-text)">
          Mọi việc cần cho một yêu cầu hỗ trợ
        </h2>
        <p className="text-[17px] leading-relaxed text-(--landing-text-dim)">
          Từ gửi yêu cầu đến phân công, xử lý và xác nhận kết quả. Công việc, tri thức và thông báo
          được kết nối để các bên cùng theo dõi tiến độ.
        </p>
      </Reveal>

      {/* Vùng cuộn: chiều cao do JS đặt = N × chiều cao 1 panel */}
      <div ref={scrollAreaRef} className="relative">
        {/* Khung dính viewport (từ lg trở lên), cắt phần panel đang trượt vào */}
        <div
          ref={stickyWrapRef}
          className="relative overflow-visible bg-white lg:sticky lg:overflow-hidden"
          style={{ top: NAVBAR_H }}
        >
          {SERVICES.map((service, i) => (
            <div
              key={service.title}
              ref={(el) => {
                panelRefs.current[i] = el;
              }}
              className={`relative w-full bg-white px-6 py-12 lg:absolute lg:inset-x-0 lg:top-0 lg:px-14 ${
                i > 0
                  ? "border-t border-(--landing-border) lg:rounded-t-[20px] lg:shadow-[0_-8px_40px_rgba(0,0,0,0.06)]"
                  : ""
              }`}
            >
              <div className="mx-auto max-w-360">
                <div className="mb-6 flex items-center gap-2">
                  <span className="grid size-7 place-items-center rounded-full bg-(--landing-accent) text-[13px] font-bold text-white">
                    {String(i + 1).padStart(2, "0")}
                  </span>
                  <span className="text-xs font-semibold tracking-[0.08em] text-(--landing-text-dim) uppercase">
                    Tính năng chính
                  </span>
                </div>

                <div className="grid items-start gap-10 lg:grid-cols-2 lg:gap-14">
                  <div>
                    <h3 className="mb-4 text-[clamp(28px,3.5vw,46px)] leading-[1.1] font-extrabold tracking-tight text-(--landing-text)">
                      {service.title}
                    </h3>
                    <p className="mb-6 text-[17px] leading-relaxed text-(--landing-text-dim)">
                      {service.desc}
                    </p>
                    <a
                      href="#roles"
                      className="mb-8 inline-flex items-center gap-2 rounded-[10px] border-[1.5px] border-(--landing-accent) px-6 py-3 text-base font-bold text-(--landing-accent) transition-colors hover:bg-(--landing-accent) hover:text-white"
                    >
                      {service.linkLabel}
                      <ArrowRight className="size-4" />
                    </a>
                    <div className="flex flex-col">
                      {service.features.map((f, fi) => (
                        <div
                          key={f.title}
                          className={`flex gap-3.5 py-5 ${fi < service.features.length - 1 ? "border-b border-(--landing-border)" : ""} ${fi === 0 ? "pt-0" : ""}`}
                        >
                          <div className="mt-1 grid size-12 shrink-0 place-items-center rounded-xl bg-(--landing-text)">
                            <f.icon className="size-6 text-white" />
                          </div>
                          <div>
                            <h4 className="mb-1 text-lg font-bold text-(--landing-text)">
                              {f.title}
                            </h4>
                            <p className="text-base leading-relaxed text-(--landing-text-dim)">
                              {f.desc}
                            </p>
                          </div>
                        </div>
                      ))}
                    </div>
                  </div>

                  <div
                    className="min-w-0 overflow-hidden rounded-[20px] border border-(--landing-border) shadow-[0_1px_3px_rgba(0,0,0,0.08),0_4px_16px_rgba(0,0,0,0.06)]"
                  >
                    <img
                      src={service.image}
                      alt={service.imageAlt}
                      width={service.imageWidth}
                      height={service.imageHeight}
                      loading="lazy"
                      decoding="async"
                      className="block h-auto w-full"
                    />
                  </div>
                </div>
              </div>
            </div>
          ))}
        </div>
      </div>
    </section>
  );
};
