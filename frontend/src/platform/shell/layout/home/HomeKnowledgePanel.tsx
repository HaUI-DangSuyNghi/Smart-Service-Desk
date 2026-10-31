import { Link } from "react-router";
import { IconArrowUpRight, IconBook, IconFileText, IconSparkles } from "@tabler/icons-react";
import { useKnowledgeList } from "@/platform/data/knowledge";
import { formatRelativeTime } from "@/platform/shared/lib/format";
import { HomePanel, HomePanelContent } from "./HomePanel";

export const HomeKnowledgePanel = () => {
  const query = useKnowledgeList("", 0);
  return (
    <HomePanel
      title="Kho tri thức"
      icon={IconBook}
      to="/knowledge"
      total={query.isSuccess ? query.data.total : undefined}
      actions={
        <Link
          to="/knowledge-chat"
          className="m-3 flex min-h-11 items-center gap-2 rounded-crm-sm border border-crm-line bg-crm-subtle px-3 text-crm-md text-crm-text outline-none hover:bg-crm-hover focus-visible:ring-2 focus-visible:ring-crm-focus"
        >
          <IconSparkles size={16} className="text-crm-accent" />
          <span className="min-w-0 flex-1 font-medium">Hỏi đáp tài liệu</span>
          <IconArrowUpRight size={16} className="shrink-0 text-crm-text-3" />
        </Link>
      }
    >
      <HomePanelContent
        isPending={query.isPending}
        error={query.error}
        isEmpty={!query.data?.items.length}
        emptyMessage="Chưa có tài liệu nào."
        label="Đang tải tài liệu"
        onRetry={() => void query.refetch()}
      >
        {query.data?.items.slice(0, 4).map((item) => (
          <Link
            key={item.id}
            to={`/knowledge/${item.id}`}
            className="flex min-w-0 items-start gap-2.5 px-4 py-3 outline-none transition-colors duration-100 hover:bg-crm-hover focus-visible:bg-crm-accent-soft focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-crm-focus"
          >
            <IconFileText size={16} className="mt-0.5 shrink-0 text-crm-text-3" />
            <div className="min-w-0 flex-1">
              <p className="truncate text-crm-text">{item.title}</p>
              <div className="mt-1 flex flex-wrap gap-x-2 gap-y-1 text-crm-xs text-crm-text-3">
                <span className="max-w-full truncate">{item.source}</span>
                <span>{formatRelativeTime(item.createdAt)}</span>
              </div>
            </div>
          </Link>
        ))}
      </HomePanelContent>
    </HomePanel>
  );
};
