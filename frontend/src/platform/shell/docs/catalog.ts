import { DOCS } from "./articles";
export { DOCS, DOC_GROUPS } from "./articles";

const content = import.meta.glob<string>("./content/*.md", {
  query: "?raw",
  import: "default",
  eager: true,
});

export const DOCS_PATH = "/docs";

export const docPath = (id?: string) => (id ? `${DOCS_PATH}/${id}` : DOCS_PATH);

export const articleContent = (id: string) => content[`./content/${id}.md`] ?? "";

export const normalizeSearch = (value: string) =>
  value
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/đ/g, "d")
    .replace(/Đ/g, "D")
    .toLowerCase();

export const headingId = (title: string) =>
  normalizeSearch(title)
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-|-$/g, "");

export const articleHeadings = (markdown: string) => {
  const used = new Map<string, number>();
  let fenced = false;
  return markdown.split("\n").flatMap((line, index) => {
    if (line.startsWith("```")) fenced = !fenced;
    const match = !fenced && /^(#{2,3}) (.+)$/.exec(line);
    if (!match) return [];
    const title = match[2].replace(/\*\*|`/g, "");
    const slug = headingId(title);
    const count = used.get(slug) ?? 0;
    used.set(slug, count + 1);
    return [
      { title, id: count ? `${slug}-${count}` : slug, line: index + 1, level: match[1].length },
    ];
  });
};

/** Tìm trong nội dung từng mục, hỗ trợ tiếng Việt không dấu. */
export const searchArticles = (query: string) => {
  const terms = normalizeSearch(query.trim()).split(/\s+/).filter(Boolean);
  if (!terms.length) return [];
  return DOCS.flatMap((article) => {
    const markdown = articleContent(article.id).replace(/<!--[^]*?-->/g, "");
    const headings = articleHeadings(markdown);
    return markdown.split(/(?=^## )/m).flatMap((section) => {
      const plain = section
        .replace(/\[([^\]]+)\]\([^)]+\)/g, "$1")
        .replace(/[#*`>|]/g, "")
        .trim();
      const normalized = normalizeSearch(`${article.title} ${plain}`);
      if (!terms.every((term) => normalized.includes(term))) return [];
      const title = /^## (.+)/.exec(section)?.[1];
      const heading = headings.find((item) => item.title === title);
      const hit = normalizeSearch(plain).indexOf(terms[0]);
      const start = Math.max(0, hit - 45);
      return [
        {
          article,
          title: title ?? article.title,
          href: `${docPath(article.id)}${heading ? `#${heading.id}` : ""}`,
          excerpt: `${start ? "…" : ""}${plain.slice(start, start + 170)}${plain.length > start + 170 ? "…" : ""}`,
        },
      ];
    });
  }).slice(0, 12);
};
