import { useEffect, useState, type ReactNode } from "react";
import { Link } from "react-router";
import { ChevronDown, Menu, X } from "lucide-react";
import { cn } from "cn";
import { useAuth } from "@/platform/human/auth";
import { APP_HOME, repoFile } from "./links";
import { Wordmark } from "./Wordmark";

const PRODUCT_ITEMS = [
  { label: "Ticket & quy trình", href: "#services" },
  { label: "AI Triage có giải thích", href: "#services" },
  { label: "Dữ liệu & tri thức", href: "#services" },
  { label: "Phân quyền theo workspace", href: "#roles" },
];

const RESOURCE_ITEMS = [
  {
    label: "Hợp đồng API",
    desc: "OpenAPI cho Core, H-P-D-I và Service Desk",
    href: repoFile("contracts/README.md"),
  },
  {
    label: "Kiến trúc 3 tầng",
    desc: "Open-Core, H-P-D-I và ứng dụng nghiệp vụ",
    href: repoFile("docs/core-architecture.md"),
  },
];

const NAV_LINK = "rounded-lg px-3.5 py-2 text-lg font-bold transition-all";

interface DropdownProps {
  label: string;
  children: ReactNode;
  wide?: boolean;
  scrolled: boolean;
}

const DesktopDropdown = ({ label, children, wide, scrolled }: DropdownProps) => {
  return (
    <div className="group relative">
      <button
        className={cn(
          "flex items-center gap-1 rounded-lg px-3.5 py-2 text-lg font-bold transition-all",
          scrolled ? "text-gray-700 hover:bg-black/5" : "text-white hover:bg-white/10",
        )}
      >
        {label}
        <ChevronDown className="size-3.5 transition-transform group-hover:rotate-180" />
      </button>
      <div
        className={cn(
          "invisible absolute top-full left-0 translate-y-2 rounded-2xl border border-(--landing-border) bg-(--landing-surface) opacity-0 shadow-[0_16px_48px_rgba(0,0,0,0.12)] transition-all duration-200 group-hover:visible group-hover:translate-y-1 group-hover:opacity-100",
          wide ? "left-1/2 w-160 -translate-x-1/3" : "w-75",
        )}
      >
        {children}
      </div>
    </div>
  );
};

export const LandingHeader = () => {
  const [scrolled, setScrolled] = useState(false);
  const [mobileOpen, setMobileOpen] = useState(false);
  // Đã đăng nhập (phiên khôi phục được) thì mời vào thẳng ứng dụng
  const { status } = useAuth();
  const ctaLabel = status === "authenticated" ? "Vào ứng dụng" : "Đăng nhập";

  useEffect(() => {
    const onScroll = () => {
      setScrolled(window.scrollY > 10);
    };
    window.addEventListener("scroll", onScroll);
    return () => window.removeEventListener("scroll", onScroll);
  }, []);

  const navColor = scrolled ? "text-gray-700 hover:bg-black/5" : "text-white hover:bg-white/10";

  return (
    <>
      <nav
        className={cn(
          "fixed inset-x-0 top-0 z-50 transition-colors duration-300",
          scrolled
            ? "bg-white/95 shadow-[0_2px_20px_rgba(0,0,0,0.08)] backdrop-blur-md"
            : "bg-transparent",
        )}
      >
        <div className="mx-auto flex h-16 max-w-360 items-center justify-between px-6 lg:px-14">
          <div className="flex items-center gap-6">
            <a href="#top" className="flex items-center">
              <Wordmark light={!scrolled} />
            </a>

            <div className="hidden items-center gap-0.5 lg:flex">
              <a href="#top" className={cn(NAV_LINK, navColor)}>
                Trang chủ
              </a>

              <DesktopDropdown label="Sản phẩm" wide scrolled={scrolled}>
                <div className="grid grid-cols-[1fr_1.2fr]">
                  <div className="border-r border-[#f3f4f6] p-6">
                    <h3 className="mb-1.5 text-base font-bold text-(--landing-text)">
                      Smart Service Desk
                    </h3>
                    <p className="text-[13px] leading-relaxed text-(--landing-text-dim)">
                      Hỗ trợ CNTT cho đơn vị đại học, chạy trên nền tảng DX-OS Open-Core
                    </p>
                  </div>
                  <div className="p-3">
                    {PRODUCT_ITEMS.map((item) => (
                      <a
                        key={item.label}
                        href={item.href}
                        className="flex items-center justify-between rounded-xl px-4 py-3 text-sm font-semibold text-(--landing-text) transition-colors hover:bg-(--landing-bg-soft)"
                      >
                        {item.label}
                        <ChevronDown className="size-4 -rotate-90 text-(--landing-text-dim)" />
                      </a>
                    ))}
                  </div>
                </div>
              </DesktopDropdown>

              <a href="#architecture" className={cn(NAV_LINK, navColor)}>
                Kiến trúc
              </a>

              <DesktopDropdown label="Tài nguyên" scrolled={scrolled}>
                <div className="p-3">
                  {RESOURCE_ITEMS.map((item) => (
                    <a
                      key={item.label}
                      href={item.href}
                      target="_blank"
                      rel="noopener"
                      className="flex items-start gap-3.5 rounded-xl px-3.5 py-3 transition-colors hover:bg-(--landing-bg-soft)"
                    >
                      <span className="mt-0.5 grid size-9 shrink-0 place-items-center rounded-lg bg-(--landing-accent-soft) text-(--landing-accent)">
                        <ChevronDown className="size-4 -rotate-90" />
                      </span>
                      <span>
                        <span className="block text-[13px] font-semibold text-(--landing-text)">
                          {item.label}
                        </span>
                        <span className="mt-0.5 block text-[11px] leading-tight text-(--landing-text-dim)">
                          {item.desc}
                        </span>
                      </span>
                    </a>
                  ))}
                </div>
              </DesktopDropdown>
            </div>
          </div>

          <div className="flex items-center gap-2">
            <Link
              to={APP_HOME}
              className="hidden rounded-lg bg-(--landing-accent) px-5 py-2.5 text-[15px] font-bold text-white transition-opacity hover:opacity-90 lg:inline-flex"
            >
              {ctaLabel}
            </Link>
            <button
              onClick={() => setMobileOpen(true)}
              className={cn(
                "grid size-10 place-items-center rounded-lg lg:hidden",
                scrolled ? "text-(--landing-text)" : "text-white",
              )}
              aria-label="Mở menu"
            >
              <Menu className="size-6" />
            </button>
          </div>
        </div>
      </nav>

      <div
        className={cn(
          "fixed inset-0 z-[100] flex flex-col gap-2 bg-white/98 px-6 pt-20 pb-8 backdrop-blur-md transition-transform duration-300 lg:hidden",
          mobileOpen ? "translate-x-0" : "translate-x-full",
        )}
      >
        <button
          onClick={() => setMobileOpen(false)}
          className="absolute top-4.5 right-4.5 grid size-10 place-items-center rounded-lg border border-(--landing-border) text-(--landing-text)"
          aria-label="Đóng menu"
        >
          <X className="size-5" />
        </button>
        {[
          ["Trang chủ", "#top"],
          ["Sản phẩm", "#services"],
          ["Kiến trúc", "#architecture"],
          ["Vai trò", "#roles"],
          ["Công nghệ", "#stack"],
        ].map(([label, href]) => (
          <a
            key={label}
            href={href}
            onClick={() => setMobileOpen(false)}
            className="rounded-[10px] px-4 py-3.5 text-lg font-bold text-(--landing-text) hover:bg-(--landing-bg-soft)"
          >
            {label}
          </a>
        ))}
        <div className="mt-auto flex flex-col gap-2.5">
          <Link
            to={APP_HOME}
            onClick={() => setMobileOpen(false)}
            className="rounded-lg bg-(--landing-accent) px-5 py-2.5 text-center text-[15px] font-bold text-white"
          >
            {ctaLabel}
          </Link>
        </div>
      </div>
    </>
  );
};
