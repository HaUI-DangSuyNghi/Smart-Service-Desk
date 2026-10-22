import { Code2 } from "lucide-react";
import { REPO_URL, repoFile } from "./links";
import { Wordmark } from "./Wordmark";

const COLUMNS = [
  {
    title: "Sản phẩm",
    links: [
      { label: "Ticket & quy trình", href: "#services" },
      { label: "AI Triage", href: "#services" },
      { label: "Dữ liệu & tri thức", href: "#services" },
      { label: "Dashboard", href: "#services" },
    ],
  },
  {
    title: "Dự án",
    links: [
      { label: "Kiến trúc", href: repoFile("docs/core-architecture.md") },
      { label: "Đóng góp", href: repoFile("CONTRIBUTING.md") },
      { label: "Thay đổi", href: repoFile("CHANGELOG.md") },
      { label: "Giấy phép", href: repoFile("LICENSING.md") },
    ],
  },
  {
    title: "Tài liệu",
    links: [
      { label: "Hợp đồng API", href: repoFile("contracts/README.md") },
      { label: "Nghiệm thu Core", href: repoFile("docs/core-acceptance.md") },
      { label: "README", href: repoFile("README.md") },
    ],
  },
  {
    title: "Vai trò",
    links: [
      { label: "Người yêu cầu", href: "#roles" },
      { label: "Kỹ thuật viên", href: "#roles" },
      { label: "Điều phối viên", href: "#roles" },
    ],
  },
];

export function LandingFooter() {
  return (
    <footer className="bg-[#0c0f0e] px-6 py-16 lg:px-8">
      <div className="mx-auto max-w-360">
        <div className="mb-12 grid grid-cols-1 gap-10 sm:grid-cols-2 lg:grid-cols-[1.6fr_1fr_1fr_1fr_1fr]">
          <div>
            <div className="mb-4">
              <Wordmark light />
            </div>
            <p className="mb-5 text-sm leading-relaxed text-white/40">
              Hỗ trợ CNTT cho đơn vị đại học — ticket theo quy trình BPMN, gợi ý AI có giải thích và
              con người quyết định, chạy trên nền tảng DX-OS Open-Core.
            </p>
            <div className="flex gap-2.5">
              <a
                href={REPO_URL}
                target="_blank"
                rel="noopener"
                aria-label="GitHub"
                className="grid size-9 place-items-center rounded-lg border border-white/10 text-white/40 transition-colors hover:border-white/30 hover:text-white"
              >
                <Code2 className="size-4" />
              </a>
            </div>
          </div>

          {COLUMNS.map((col) => (
            <div key={col.title}>
              <h4 className="mb-4 text-[13px] font-bold tracking-[0.08em] text-white/50 uppercase">
                {col.title}
              </h4>
              {col.links.map((link) => (
                <a
                  key={link.label}
                  href={link.href}
                  className="mb-2.5 block text-sm text-white/40 transition-colors hover:text-white/85"
                >
                  {link.label}
                </a>
              ))}
            </div>
          ))}
        </div>

        <div className="flex flex-col items-center gap-4 border-t border-white/6 pt-6 sm:flex-row sm:justify-between">
          <p className="text-[13px] text-white/25">© 2026 Smart Service Desk — Team HaUI</p>
          <div className="flex gap-2">
            {["Java 17", "Spring Boot 3.3", "React 19"].map((badge) => (
              <span
                key={badge}
                className="rounded-md border border-white/12 px-2 py-0.5 text-[11px] font-bold text-white/35"
              >
                {badge}
              </span>
            ))}
          </div>
          <div className="flex gap-5 text-[13px] text-white/25">
            <a href={repoFile("CONTRIBUTING.md")} className="transition-colors hover:text-white/60">
              Contributing
            </a>
            <a href={repoFile("LICENSING.md")} className="transition-colors hover:text-white/60">
              Licensing
            </a>
          </div>
        </div>

        <p className="mt-6 text-center text-[11px] text-white/20">Designed by Huy Hoang</p>
      </div>
    </footer>
  );
}
