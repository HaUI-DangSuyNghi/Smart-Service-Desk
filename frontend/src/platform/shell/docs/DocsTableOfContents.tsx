import { useEffect, useState } from "react";
import { cn } from "cn";
import { articleHeadings } from "./catalog";

export const DocsTableOfContents = ({ content }: { content: string }) => {
  const headings = articleHeadings(content);
  const [active, setActive] = useState(headings[0]?.id);
  useEffect(() => {
    const elements = articleHeadings(content)
      .map((item) => document.getElementById(item.id))
      .filter((item): item is HTMLElement => item !== null);
    const observer = new IntersectionObserver(
      (entries) => {
        const visible = entries
          .filter((entry) => entry.isIntersecting)
          .sort((a, b) => a.boundingClientRect.top - b.boundingClientRect.top);
        if (visible[0]) setActive(visible[0].target.id);
      },
      { rootMargin: "-90px 0px -60% 0px", threshold: 0 },
    );
    elements.forEach((element) => observer.observe(element));
    return () => observer.disconnect();
  }, [content]);
  if (!headings.length) return null;
  return (
    <nav
      aria-label="Trong bài viết này"
      className="sticky top-25 max-h-[calc(100dvh-8rem)] overflow-y-auto pr-3 text-xs"
    >
      <p className="mb-4 font-semibold text-(--landing-text)">Trong bài viết này</p>
      <div className="border-l border-(--landing-border)">
        {headings.map((heading) => (
          <a
            key={heading.id}
            href={`#${heading.id}`}
            aria-current={active === heading.id ? "location" : undefined}
            className={cn(
              "-ml-px block border-l py-2 pl-4 leading-5 transition-colors hover:text-(--landing-accent)",
              active === heading.id
                ? "border-(--landing-accent) font-medium text-(--landing-accent)"
                : "border-transparent text-(--landing-text-dim)",
            )}
          >
            {heading.title}
          </a>
        ))}
      </div>
    </nav>
  );
};
