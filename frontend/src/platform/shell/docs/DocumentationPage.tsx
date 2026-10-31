import { useEffect } from "react";
import { Link, useLocation, useParams } from "react-router";
import { IconArrowLeft, IconArrowRight, IconChevronRight } from "@tabler/icons-react";
import { articleContent, articleHeadings, DOC_GROUPS, DOCS, docPath } from "./catalog";
import { DocsMarkdown } from "./DocsMarkdown";
import { DocsOverview } from "./DocsOverview";
import { DocsTableOfContents } from "./DocsTableOfContents";

const DocumentationPage = () => {
  const params = useParams();
  const slug = params.slug ?? params["*"];
  const { pathname, hash } = useLocation();
  const article = DOCS.find((item) => item.id === slug);
  const group = DOC_GROUPS.find((item) => item.id === article?.group);
  const index = DOCS.findIndex((item) => item.id === slug);
  const previous = DOCS[index - 1];
  const next = DOCS[index + 1];
  const content = article ? articleContent(article.id) : "";
  const hasHeadings = articleHeadings(content).length > 0;

  useEffect(() => {
    const oldTitle = document.title;
    const description = document.querySelector('meta[name="description"]');
    const oldDescription = description?.getAttribute("content");
    document.title = `${article?.title ?? (slug ? "Không tìm thấy tài liệu" : "Tài liệu & Hướng dẫn sử dụng")} | Smart Service Desk`;
    description?.setAttribute(
      "content",
      article?.description ??
        "Hướng dẫn sử dụng Smart Service Desk theo vai trò, quản lý công việc, tài liệu và hỏi đáp AI.",
    );
    return () => {
      document.title = oldTitle;
      if (oldDescription !== null && oldDescription !== undefined)
        description?.setAttribute("content", oldDescription);
    };
  }, [article, slug]);

  useEffect(() => {
    const frame = requestAnimationFrame(() => {
      let anchor = hash.slice(1);
      try {
        anchor = decodeURIComponent(anchor);
      } catch {
        /* Giữ nguyên hash nếu URL không hợp lệ. */
      }
      const target = anchor && document.getElementById(anchor);
      if (target) target.scrollIntoView();
      else window.scrollTo(0, 0);
      if (!hash) document.getElementById("docs-main")?.focus({ preventScroll: true });
    });
    return () => cancelAnimationFrame(frame);
  }, [pathname, hash]);

  if (!slug) return <DocsOverview />;
  if (!article)
    return (
      <div className="mx-auto max-w-2xl px-6 py-20">
        <p className="text-sm font-semibold text-(--landing-accent)">404 · Tài liệu</p>
        <h1 className="mt-4 text-3xl font-semibold">Không tìm thấy bài viết</h1>
        <p className="mt-4 text-(--landing-text-dim)">
          Đường dẫn có thể đã thay đổi. Bạn có thể chọn bài trong danh mục hoặc tìm theo từ khóa.
        </p>
        <Link
          to={docPath()}
          className="mt-7 inline-flex items-center gap-2 font-medium text-(--landing-accent)"
        >
          <IconArrowLeft size={17} /> Về tổng quan tài liệu
        </Link>
      </div>
    );

  return (
    <div className={hasHeadings ? "grid min-w-0 xl:grid-cols-[minmax(0,1fr)_210px]" : "min-w-0"}>
      <div className="mx-auto w-full min-w-0 max-w-215 px-5 pb-12 pt-8 sm:px-10 lg:pt-10 xl:px-12">
        <nav
          aria-label="Đường dẫn bài viết"
          className="mb-8 flex flex-wrap items-center gap-2 text-xs text-(--landing-text-dim)"
        >
          <Link to={docPath()} className="hover:text-(--landing-accent)">
            Tài liệu
          </Link>
          <IconChevronRight size={13} />
          <span>{group?.title}</span>
        </nav>
        <header className="mb-9 border-b border-(--landing-border) pb-7">
          <h1 className="text-3xl font-bold leading-tight tracking-tight sm:text-4xl">
            {article.title}
          </h1>
          <p className="mt-4 text-base leading-7 text-(--landing-text-dim)">
            {article.description}
          </p>
          <p className="mt-5 text-xs text-(--landing-text-dim)">
            Hướng dẫn sử dụng · Cập nhật 31/10/2026
          </p>
        </header>
        {hasHeadings && <details className="mb-8 rounded-[10px] border border-(--landing-border) px-4 py-3 xl:hidden">
          <summary className="cursor-pointer text-sm font-medium">Trong bài viết này</summary>
          <div className="mt-4">
            <DocsTableOfContents key={article.id} content={content} />
          </div>
        </details>}
        <DocsMarkdown content={content} />
        <div
          className="mt-14 grid gap-3 border-t border-(--landing-border) pt-7 sm:grid-cols-2"
          aria-label="Bài viết liền kề"
        >
          {previous ? (
            <Link to={docPath(previous.id)} className="docs-pagination">
              <span className="flex items-center gap-2 text-xs text-(--landing-text-dim)">
                <IconArrowLeft size={14} /> Trước đó
              </span>
              <span className="mt-2 block text-sm font-semibold">{previous.title}</span>
            </Link>
          ) : (
            <span />
          )}
          {next && (
            <Link to={docPath(next.id)} className="docs-pagination text-right">
              <span className="flex items-center justify-end gap-2 text-xs text-(--landing-text-dim)">
                Tiếp theo <IconArrowRight size={14} />
              </span>
              <span className="mt-2 block text-sm font-semibold">{next.title}</span>
            </Link>
          )}
        </div>
        <div className="mt-7 flex flex-wrap items-center justify-between gap-3 text-xs text-(--landing-text-dim)">
          <span>Smart Service Desk · Tài liệu sử dụng</span>
          <Link to={docPath("dong-gop")} className="hover:text-(--landing-accent)">
            Hỗ trợ & đóng góp <span aria-hidden="true">↗</span>
          </Link>
        </div>
      </div>
      {hasHeadings && <aside className="hidden border-l border-(--landing-border) px-5 pt-10 xl:block">
        <DocsTableOfContents key={article.id} content={content} />
      </aside>}
    </div>
  );
};

export default DocumentationPage;
