import Markdown, { type Components } from "react-markdown";
import remarkGfm from "remark-gfm";
import { Link } from "react-router";
import { IconLink } from "@tabler/icons-react";
import { articleHeadings, DOCS, docPath } from "./catalog";
import { DocsCodeBlock } from "./DocsCodeBlock";

export const DocsMarkdown = ({ content }: { content: string }) => {
  const headings = articleHeadings(content);
  const components: Components = {
    h2: ({ children, node }) => {
      const id = headings.find((item) => item.line === node?.position?.start.line)?.id;
      return (
        <h2 id={id}>
          {children}
          <a
            href={`#${id}`}
            className="docs-heading-link"
            aria-label={`Liên kết tới ${headings.find((item) => item.id === id)?.title ?? "mục này"}`}
          >
            <IconLink size={16} />
          </a>
        </h2>
      );
    },
    h3: ({ children, node }) => {
      const id = headings.find((item) => item.line === node?.position?.start.line)?.id;
      return <h3 id={id}>{children}</h3>;
    },
    a: ({ children, href }) => {
      const chapter = href?.startsWith("#") && DOCS.find((item) => item.id === href.slice(1));
      if (chapter) return <Link to={docPath(chapter.id)}>{children}</Link>;
      if (href?.startsWith("/")) return <Link to={href}>{children}</Link>;
      return (
        <a
          href={href}
          target={href?.startsWith("https://") ? "_blank" : undefined}
          rel={href?.startsWith("https://") ? "noopener noreferrer" : undefined}
        >
          {children}
        </a>
      );
    },
    pre: ({ children }) => <DocsCodeBlock>{children}</DocsCodeBlock>,
    table: ({ children }) => (
      <div className="docs-table-scroll" role="region" aria-label="Bảng thông tin" tabIndex={0}>
        <table>{children}</table>
      </div>
    ),
  };
  return (
    <div className="docs-article">
      <Markdown remarkPlugins={[remarkGfm]} components={components} skipHtml>
        {content}
      </Markdown>
    </div>
  );
};
