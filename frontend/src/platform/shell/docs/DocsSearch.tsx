import { useEffect, useState } from "react";
import { Link } from "react-router";
import { IconArrowRight, IconSearch } from "@tabler/icons-react";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/platform/shared/ui/dialog";
import { searchArticles } from "./catalog";

export const DocsSearch = () => {
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState("");
  const results = searchArticles(query);
  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === "k") {
        event.preventDefault();
        setOpen((value) => !value);
      }
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, []);
  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <button
        type="button"
        onClick={() => setOpen(true)}
        className="docs-search-trigger"
        aria-label="Tìm trong tài liệu"
      >
        <IconSearch size={18} />
        <span className="hidden sm:inline">Tìm trong tài liệu…</span>
        <kbd className="ml-8 hidden rounded border border-(--landing-border) px-1.5 py-0.5 text-[11px] lg:inline">
          Ctrl K
        </kbd>
      </button>
      <DialogContent className="landing docs-search-dialog gap-0 p-0 sm:max-w-2xl">
        <DialogHeader className="sr-only">
          <DialogTitle>Tìm trong tài liệu</DialogTitle>
          <DialogDescription>
            Tìm nội dung hướng dẫn theo từ khóa, có thể nhập tiếng Việt không dấu.
          </DialogDescription>
        </DialogHeader>
        <div className="flex items-center gap-3 border-b border-(--landing-border) py-5 pl-5 pr-12">
          <IconSearch size={20} className="shrink-0 text-(--landing-text-dim)" />
          <input
            autoFocus
            type="search"
            aria-label="Từ khóa tài liệu"
            placeholder="Tìm hướng dẫn, chức năng, lỗi…"
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            className="min-w-0 flex-1 bg-transparent text-base outline-none"
          />
        </div>
        <div className="max-h-[60dvh] overflow-y-auto p-2" aria-live="polite">
          {!query.trim() ? (
            <div className="px-4 py-8 text-sm leading-7 text-(--landing-text-dim)">
              Thử tìm “tạo ticket”, “nguồn tài liệu” hoặc “lỗi Docker”.
            </div>
          ) : results.length ? (
            <ul aria-label="Kết quả tìm kiếm" className="space-y-1">
              {results.map((result) => (
                <li key={result.href}>
                  <Link
                    to={result.href}
                    onClick={() => setOpen(false)}
                    className="group block rounded-xl px-4 py-3 hover:bg-(--landing-bg-soft) focus-visible:outline-2 focus-visible:outline-(--landing-accent)"
                  >
                    <span className="text-xs text-(--landing-text-dim)">
                      {result.article.title}
                    </span>
                    <span className="mt-1 flex items-center justify-between gap-3 text-sm font-semibold">
                      {result.title}
                      <IconArrowRight size={16} className="shrink-0 text-(--landing-accent)" />
                    </span>
                    <span className="mt-1 block text-xs leading-5 text-(--landing-text-dim)">
                      {result.excerpt}
                    </span>
                  </Link>
                </li>
              ))}
            </ul>
          ) : (
            <p className="px-4 py-8 text-sm text-(--landing-text-dim)">
              Không tìm thấy nội dung phù hợp. Thử từ khóa ngắn hơn.
            </p>
          )}
        </div>
        <div className="border-t border-(--landing-border) px-5 py-3 text-xs text-(--landing-text-dim)">
          Tìm trong toàn bộ hướng dẫn · Esc để đóng
        </div>
      </DialogContent>
    </Dialog>
  );
};
