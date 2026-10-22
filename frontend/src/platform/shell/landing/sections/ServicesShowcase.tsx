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
    title: "Ticket & Quy trình BPMN",
    desc: "Mỗi ticket chạy theo một quy trình BPMN trên Flowable: kỹ thuật viên xử lý, người yêu cầu xác nhận hoặc yêu cầu làm lại. Trạng thái và lịch sử luôn lấy từ engine, không có cột trạng thái tự chế.",
    linkLabel: "Khám phá quy trình",
    tone: "from-[#1b330f] to-[#112009]",
    features: [
      {
        icon: GitBranch,
        title: "Lịch sử từ engine",
        desc: "Timeline hiện từng bước: ai làm, lúc nào, kết quả và ghi chú — đọc trực tiếp từ Flowable.",
      },
      {
        icon: Hand,
        title: "Nhận việc không trùng",
        desc: "Hai người cùng nhận một việc thì người sau bị từ chối, tránh xử lý chồng chéo.",
      },
      {
        icon: RefreshCw,
        title: "Xác nhận hoặc làm lại",
        desc: "Người yêu cầu là người quyết định ticket đã xong; chưa đạt thì quay lại bước xử lý.",
      },
    ],
  },
  {
    title: "AI Triage có giải thích",
    desc: "Dịch vụ Intelligence đọc tiêu đề và mô tả, đề xuất nhóm, mức ưu tiên và nhóm xử lý theo bộ luật có phiên bản. Gợi ý luôn kèm lý do — và chỉ là gợi ý, con người quyết định.",
    linkLabel: "Khám phá AI Triage",
    tone: "from-[#273211] to-[#181f0a]",
    features: [
      {
        icon: Brain,
        title: "Gợi ý kèm bằng chứng",
        desc: "Mỗi đề xuất có danh sách luật khớp, đoạn văn bản làm căn cứ và cờ cần xem lại.",
      },
      {
        icon: ListChecks,
        title: "Không tự áp dụng",
        desc: "Kết quả AI không bao giờ tự thay đổi ticket; người xử lý luôn là người chốt.",
      },
      {
        icon: ShieldCheck,
        title: "Vẫn chạy khi AI ngừng",
        desc: "AI chậm hoặc ngừng thì phản hồi ở chế độ DEGRADED, ticket vẫn tạo và xử lý thủ công bình thường.",
      },
    ],
  },
  {
    title: "Dữ liệu & Tri thức",
    desc: "Dữ liệu và tài liệu tri thức được lưu ở Core Data kèm nguồn gốc, phiên bản, giấy phép và checksum. Thống kê đọc trực tiếp từ quy trình, đúng phạm vi quyền của từng người.",
    linkLabel: "Khám phá dữ liệu",
    tone: "from-[#11271c] to-[#0b1912]",
    features: [
      {
        icon: BookOpen,
        title: "Knowledge Hub",
        desc: "Tài liệu hướng dẫn có nguồn và phiên bản rõ ràng, chỉ người trong workspace mới đọc được.",
      },
      {
        icon: ScrollText,
        title: "Audit cùng transaction",
        desc: "Mọi thao tác ghi đều có bản ghi audit; thao tác lỗi thì audit cũng được hoàn tác.",
      },
      {
        icon: LifeBuoy,
        title: "Thống kê theo quyền",
        desc: "Người yêu cầu thấy số liệu của mình, kỹ thuật viên và điều phối viên thấy cả workspace.",
      },
    ],
  },
];

const NAVBAR_H = 64; // = h-16 của LandingHeader
const MOBILE_BREAKPOINT = 768; // khớp breakpoint md: của Tailwind

function easeInOutCubic(t: number) {
  return t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
}

function clamp(v: number, min: number, max: number) {
  return Math.min(Math.max(v, min), max);
}

export function ServicesShowcase() {
  const scrollAreaRef = useRef<HTMLDivElement>(null);
  const stickyWrapRef = useRef<HTMLDivElement>(null);
  const panelRefs = useRef<(HTMLDivElement | null)[]>([]);

  useEffect(() => {
    const scrollArea = scrollAreaRef.current;
    const stickyWrap = stickyWrapRef.current;
    const panels = panelRefs.current.filter((p): p is HTMLDivElement => p !== null);
    if (!scrollArea || !stickyWrap) return;
    const N = panels.length;
    const isMobile = () => window.innerWidth <= MOBILE_BREAKPOINT;

    let raf: number | null = null;
    let resizeTimer: ReturnType<typeof setTimeout> | undefined;
    let cancelled = false;

    // Đo panel cao nhất, đẩy panel 2..N xuống dưới, đặt chiều cao vùng cuộn = N panel
    function setup() {
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
    }

    // Mỗi lần cuộn: tính tiến độ [0,1] trong vùng cuộn, chia đều cho từng panel trượt lên
    function onScroll() {
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
    }

    function tick() {
      onScroll();
      raf = requestAnimationFrame(tick);
    }

    function start() {
      if (cancelled) return;
      setup();
      raf = requestAnimationFrame(tick);
    }

    if (document.fonts?.ready) {
      document.fonts.ready.then(start);
    } else {
      setTimeout(start, 300);
    }

    function onResize() {
      clearTimeout(resizeTimer);
      resizeTimer = setTimeout(setup, 150);
    }
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
          Từ Yêu cầu đến Hoàn tất — Có Căn cứ ở Mọi Bước
        </h2>
        <p className="text-[17px] leading-relaxed text-(--landing-text-dim)">
          Smart Service Desk biến yêu cầu hỗ trợ rời rạc thành quy trình có trạng thái, người xử lý
          và lịch sử rõ ràng — AI giúp phân loại nhanh hơn, con người giữ quyền quyết định.
        </p>
      </Reveal>

      {/* Vùng cuộn: chiều cao do JS đặt = N × chiều cao 1 panel */}
      <div ref={scrollAreaRef} className="relative">
        {/* Khung dính viewport (từ md trở lên), cắt phần panel đang trượt vào */}
        <div
          ref={stickyWrapRef}
          className="relative overflow-visible bg-white md:sticky md:overflow-hidden"
          style={{ top: NAVBAR_H }}
        >
          {SERVICES.map((service, i) => (
            <div
              key={service.title}
              ref={(el) => {
                panelRefs.current[i] = el;
              }}
              className={`relative w-full bg-white px-6 py-12 md:absolute md:inset-x-0 md:top-0 lg:px-14 ${
                i > 0
                  ? "border-t border-(--landing-border) md:rounded-t-[20px] md:shadow-[0_-8px_40px_rgba(0,0,0,0.06)]"
                  : ""
              }`}
            >
              <div className="mx-auto max-w-360">
                <div className="mb-6 flex items-center gap-2">
                  <span className="grid size-7 place-items-center rounded-full bg-(--landing-accent) text-[13px] font-bold text-white">
                    {String(i + 1).padStart(2, "0")}
                  </span>
                  <span className="text-xs font-semibold tracking-[0.08em] text-(--landing-text-dim) uppercase">
                    Năng lực cốt lõi
                  </span>
                </div>

                <div className="grid items-start gap-14 lg:grid-cols-2">
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
                    className={`aspect-4/3 overflow-hidden rounded-[20px] border border-(--landing-border) bg-linear-to-br shadow-[0_1px_3px_rgba(0,0,0,0.08),0_4px_16px_rgba(0,0,0,0.06)] ${service.tone} flex items-center justify-center`}
                  >
                    <span className="text-xs font-semibold tracking-wide text-white/50 uppercase">
                      {service.title}
                    </span>
                  </div>
                </div>
              </div>
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}
