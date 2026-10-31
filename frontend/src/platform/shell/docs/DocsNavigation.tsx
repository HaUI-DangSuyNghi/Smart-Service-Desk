import { Link, NavLink } from "react-router";
import { IconArrowUpRight, IconBook2 } from "@tabler/icons-react";
import { DOC_GROUPS, DOCS, docPath } from "./catalog";

export const DocsNavigation = ({ onNavigate }: { onNavigate?: () => void }) => (
  <nav aria-label="Danh mục tài liệu" className="docs-navigation">
    <NavLink
      to={docPath()}
      end
      onClick={onNavigate}
      className="docs-nav-link mb-6 flex items-center gap-2"
    >
      <IconBook2 size={17} /> Tổng quan tài liệu
    </NavLink>
    {DOC_GROUPS.map((group) => (
      <div key={group.id} className="mb-6">
        <p className="mb-2 px-3 text-xs font-semibold tracking-wide text-(--landing-text-dim)">
          {group.title}
        </p>
        {DOCS.filter((article) => article.group === group.id).map((article) => (
          <NavLink
            key={article.id}
            to={docPath(article.id)}
            onClick={onNavigate}
            className="docs-nav-link"
          >
            {article.title}
          </NavLink>
        ))}
      </div>
    ))}
    <Link
      to="/"
      onClick={onNavigate}
      className="mt-3 flex items-center justify-between border-t border-(--landing-border) px-3 pt-5 text-sm text-(--landing-text-dim) hover:text-(--landing-accent)"
    >
      Về trang giới thiệu <IconArrowUpRight size={15} />
    </Link>
  </nav>
);
