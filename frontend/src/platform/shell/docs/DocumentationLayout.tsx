import { useState } from "react";
import { Link, Outlet } from "react-router";
import { IconArrowUpRight, IconBook2, IconMenu2 } from "@tabler/icons-react";
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/platform/shared/ui/dialog";
import { APP_HOME } from "../landing/links";
import { Wordmark } from "../landing/Wordmark";
import { DocsNavigation } from "./DocsNavigation";
import { DocsSearch } from "./DocsSearch";
import "../landing/landing.css";
import "./documentation.css";

const DocumentationLayout = () => {
  const [menuOpen, setMenuOpen] = useState(false);
  return (
    <div className="landing documentation min-h-dvh">
      <a href="#docs-main" className="docs-skip-link">
        Chuyển tới nội dung
      </a>
      <header className="docs-header sticky top-0 z-40 border-b border-(--landing-border) bg-(--landing-surface)">
        <div className="mx-auto flex h-17 max-w-400 items-center justify-between gap-3 px-4 sm:px-6 lg:px-8">
          <div className="flex min-w-0 items-center gap-4">
            <Link to="/" aria-label="Smart Service Desk — Trang chủ" className="docs-wordmark">
              <Wordmark light={false} />
            </Link>
            <Link
              to="/docs"
              className="hidden border-l border-(--landing-border) pl-4 text-sm font-medium text-(--landing-text-dim) md:block"
            >
              Tài liệu
            </Link>
          </div>
          <div className="flex shrink-0 items-center gap-2 sm:gap-4">
            <DocsSearch />
            <Link
              to={APP_HOME}
              className="hidden items-center gap-2 rounded-[8px] bg-(--landing-accent) px-3.5 py-2 text-sm font-semibold text-white hover:bg-(--landing-accent-hover) sm:flex"
            >
              Vào ứng dụng <IconArrowUpRight size={16} />
            </Link>
            <button
              type="button"
              onClick={() => setMenuOpen(true)}
              aria-label="Mở danh mục tài liệu"
              className="grid size-10 place-items-center rounded-[8px] hover:bg-(--landing-bg-soft) lg:hidden"
            >
              <IconMenu2 size={22} />
            </button>
          </div>
        </div>
      </header>
      <div className="mx-auto grid max-w-400 lg:grid-cols-[252px_minmax(0,1fr)] xl:grid-cols-[272px_minmax(0,1fr)]">
        <aside className="sticky top-17 hidden h-[calc(100dvh-4.25rem)] overflow-y-auto border-r border-(--landing-border) px-5 py-8 lg:block">
          <DocsNavigation />
        </aside>
        <main id="docs-main" tabIndex={-1} className="min-w-0 outline-none">
          <Outlet />
        </main>
      </div>
      <Dialog open={menuOpen} onOpenChange={setMenuOpen}>
        <DialogContent className="landing documentation docs-mobile-menu">
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2 text-base">
              <IconBook2 size={20} /> Tài liệu
            </DialogTitle>
          </DialogHeader>
          <div className="min-h-0 overflow-y-auto py-3">
            <DocsNavigation onNavigate={() => setMenuOpen(false)} />
          </div>
          <Link
            to={APP_HOME}
            onClick={() => setMenuOpen(false)}
            className="rounded-[8px] bg-(--landing-accent) px-4 py-3 text-center text-sm font-semibold text-white"
          >
            Vào ứng dụng
          </Link>
        </DialogContent>
      </Dialog>
    </div>
  );
};

export default DocumentationLayout;
