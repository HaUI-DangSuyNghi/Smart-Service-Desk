import { useState } from "react";
import { useNavigate } from "react-router";
import { useQuery } from "@tanstack/react-query";
import {
  IconChevronLeft,
  IconChevronRight,
  IconRefresh,
  IconCalendarTime,
} from "@tabler/icons-react";
import { useCurrentWorkspace, usePeopleNames, personName } from "@/platform/human/workspace";
import type { WorkItem, QueueItem } from "@/platform/services";
import { getCalendarWork, getCoordinationWork } from "@/platform/services/workCalendarService";
import { useFallbackInterval } from "@/platform/shared/realtime";
import { formatDateTime } from "@/platform/shared/lib/format";
import { Button } from "@/platform/shared/ui/button";
import { DataTable, type DataTableColumn } from "@/platform/shared/ui/DataTable";
import { EmptyState } from "@/platform/shared/ui/EmptyState";
import { ErrorState } from "@/platform/shared/ui/ErrorState";
import { Input } from "@/platform/shared/ui/input";
import { PageBody, PageToolbar } from "@/platform/shared/ui/page";
import { SelectField } from "@/platform/shared/ui/SelectField";
import { TableSkeleton } from "@/platform/shared/ui/Skeleton";
import { Tag } from "@/platform/shared/ui/Tag";
import { useProcessLabels } from "../../catalog/useProcessCatalog";
import { BusinessKeyLink } from "../../common/BusinessKeyLink";
import { inboxAction, inboxLabel } from "../../common/inboxActions";
import { processLink } from "../../common/processLinks";
import { processKeys } from "../../common/queryKeys";
import { AssignTaskDialog } from "../../tasks/AssignTaskDialog";
import { PriorityTag } from "../../work/PriorityTag";
import { useNow } from "../../work/useNow";
import { WorkActions } from "../../work/WorkActions";
import { WORK_STATE_LABELS } from "../../work/workLabels";
import { WorkStateTag } from "../../work/WorkStateTag";
import { calendarDate, parseCalendarDate, shiftCalendarDate } from "./calendarModel";
import { WorkDatePicker } from "./WorkDatePicker";

const normalize = (value: string) =>
  value
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/đ/gi, "d")
    .toLocaleLowerCase("vi");
const execution = (task: WorkItem): QueueItem | null =>
  "workState" in task ? (task as QueueItem) : null;

/** One daily table; the role controls the source and available actions. */
export const DailyWorkCalendar = ({ coordinating = false }: { coordinating?: boolean }) => {
  const { current } = useCurrentWorkspace();
  const labels = useProcessLabels();
  const navigate = useNavigate();
  const today = calendarDate(useNow());
  const [selected, setSelected] = useState<string | null>(null);
  const date = selected ?? today;
  const [search, setSearch] = useState("");
  const [filter, setFilter] = useState("");
  const [assigning, setAssigning] = useState<WorkItem | null>(null);
  const interval = useFallbackInterval(30_000);
  const query = useQuery<WorkItem[]>({
    queryKey: [...processKeys.all(current), coordinating ? "coordination-calendar" : "calendar"],
    queryFn: ({ signal }) =>
      coordinating ? getCoordinationWork(current!, signal) : getCalendarWork(current!, signal),
    enabled: current !== null,
    retry: false,
    refetchInterval: interval,
  });
  const tasks = (query.isSuccess ? query.data : []).filter(
    (task) => !coordinating || task.candidateGroups.length > 0,
  );
  const names = usePeopleNames(tasks.map((task) => task.assignee));
  const dayTasks = tasks.filter((task) => calendarDate(new Date(task.createdAt)) === date);
  const labelTasks = dayTasks.filter(
    (task) => !execution(task)?.label && inboxLabel(task.processKey),
  );
  const titles = useQuery({
    queryKey: [
      ...processKeys.all(current),
      "daily-work-titles",
      date,
      ...labelTasks.map((task) => task.id),
    ],
    queryFn: async ({ signal }) => {
      const results = await Promise.allSettled(
        labelTasks.map(
          async (task) =>
            [task.id, await inboxLabel(task.processKey)!(current!, task, signal)] as const,
        ),
      );
      return Object.fromEntries(
        results.flatMap((result) =>
          result.status === "fulfilled" && typeof result.value[1] === "string"
            ? [result.value]
            : [],
        ),
      );
    },
    enabled: current !== null && labelTasks.length > 0,
    retry: false,
    staleTime: 30_000,
  });
  const options = Object.entries(WORK_STATE_LABELS).map(([value, label]) => ({ value, label }));
  const term = normalize(search.trim());
  const items = dayTasks
    .filter(
      (task) =>
        (coordinating || !filter || execution(task)?.workState === filter) &&
        (!term ||
          normalize(
            `${titles.data?.[task.id] ?? execution(task)?.label ?? ""} ${task.businessKey} ${labels.taskName(task.processKey, task.key, task.name)} ${labels.processName(task.processKey)} ${task.assignee ? personName(names, task.assignee) : ""}`,
          ).includes(term)),
    )
    .sort((a, b) => Date.parse(a.createdAt) - Date.parse(b.createdAt) || a.id.localeCompare(b.id));
  const open = (task: WorkItem) =>
    navigate(
      processLink(task.processKey, task.businessKey) ?? `/processes/${task.processInstanceId}`,
    );
  const columns: DataTableColumn<WorkItem>[] = [
    {
      key: "content",
      header: "Nội dung yêu cầu / công việc",
      width: "320px",
      cell: (task) => {
        return (
          <span className="font-medium">
            {titles.data?.[task.id] ||
              execution(task)?.label ||
              labels.taskName(task.processKey, task.key, task.name)}
          </span>
        );
      },
    },
    {
      key: "businessKey",
      header: "Mã yêu cầu",
      width: "120px",
      cell: (task) => (
        <BusinessKeyLink processKey={task.processKey} businessKey={task.businessKey} />
      ),
    },
    {
      key: "step",
      header: "Bước công việc",
      width: "180px",
      cell: (task) => labels.taskName(task.processKey, task.key, task.name),
    },
    {
      key: "state",
      header: "Trạng thái",
      width: "130px",
      cell: (task) => {
        const work = execution(task);
        return work ? (
          <WorkStateTag state={work.workState} pendingReason={work.pendingReason} />
        ) : (
          <Tag tone={task.assignee ? "blue" : "orange"}>
            {task.assignee ? "Đã giao" : "Chưa giao"}
          </Tag>
        );
      },
    },
  ];
  if (coordinating)
    columns.push({
      key: "assignee",
      header: "Người xử lý",
      width: "160px",
      cell: (task) => (task.assignee ? personName(names, task.assignee) : "Chưa giao"),
    });
  else
    columns.push(
      {
        key: "priority",
        header: "Ưu tiên",
        width: "100px",
        cell: (task) => {
          const work = execution(task);
          return work ? (
            <PriorityTag priority={work.priority} escalated={work.priority !== work.basePriority} />
          ) : (
            "—"
          );
        },
      },
      {
        key: "deadline",
        header: "Hạn xử lý",
        width: "150px",
        cell: (task) => {
          const deadline = execution(task)?.sla?.resolveBy;
          return deadline ? (
            <time dateTime={deadline}>{formatDateTime(deadline)}</time>
          ) : (
            "Chưa có hạn"
          );
        },
      },
    );
  columns.push(
    {
      key: "createdAt",
      header: "Phát sinh lúc",
      width: "150px",
      cell: (task) => <time dateTime={task.createdAt}>{formatDateTime(task.createdAt)}</time>,
    },
    {
      key: "action",
      header: "Thao tác",
      fit: true,
      cell: (task) => {
        const work = execution(task);
        if (!coordinating) return work ? <WorkActions item={work} compact /> : null;
        const Action = inboxAction(task.processKey);
        const fallback = (
          <Button size="sm" variant="info" className="w-full" onClick={() => setAssigning(task)}>
            {task.assignee ? "Giao lại" : "Giao việc"}
          </Button>
        );
        return (
          <span
            onClick={(event) => event.stopPropagation()}
            onKeyDown={(event) => event.stopPropagation()}
          >
            {Action ? <Action task={task} fallback={fallback} /> : fallback}
          </span>
        );
      },
    },
  );
  const chooseDate = (value: string) => {
    setSelected(value === today ? null : value);
    setFilter("");
  };
  return (
    <>
      <PageToolbar
        start={
          <div className="flex flex-wrap items-center gap-1" aria-label="Chọn ngày công việc">
            <Button
              variant="outline"
              size="icon-sm"
              className="size-9 md:size-7"
              aria-label="Ngày trước"
              onClick={() => chooseDate(shiftCalendarDate(date, -1))}
            >
              <IconChevronLeft size={16} />
            </Button>
            <Button
              variant="outline"
              size="sm"
              className="min-h-9 md:min-h-7"
              onClick={() => chooseDate(today)}
            >
              Hôm nay
            </Button>
            <WorkDatePicker selected={date} today={today} onSelect={chooseDate} />
            <Button
              variant="outline"
              size="icon-sm"
              className="size-9 md:size-7"
              aria-label="Ngày sau"
              onClick={() => chooseDate(shiftCalendarDate(date, 1))}
            >
              <IconChevronRight size={16} />
            </Button>
          </div>
        }
        end={
          <div className="flex w-full min-w-0 flex-wrap items-center gap-2">
            <Input
              aria-label="Tìm công việc"
              placeholder="Tìm công việc…"
              value={search}
              onChange={(event) => setSearch(event.target.value)}
              className="h-9 min-w-0 flex-1 sm:w-56 sm:flex-none md:h-7"
            />
            {!coordinating && (
              <SelectField
                aria-label="Trạng thái công việc"
                placeholder="Mọi trạng thái"
                value={filter}
                onValueChange={setFilter}
                options={options}
                className="min-h-9 md:h-7"
              />
            )}
            <Button
              variant="outline"
              size="icon-sm"
              className="size-9 md:size-7"
              aria-label="Làm mới lịch"
              disabled={query.isFetching}
              onClick={() => void query.refetch()}
            >
              <IconRefresh size={16} />
            </Button>
          </div>
        }
      />
      <PageBody>
        <section aria-label="Công việc trong ngày" className="flex min-w-0 flex-col">
          <div className="flex flex-wrap items-center justify-between gap-2 border-b border-crm-line px-4 py-3 text-crm-sm">
            <div>
              <h2 className="font-medium">
                {parseCalendarDate(date).toLocaleDateString("vi-VN", {
                  weekday: "long",
                  day: "2-digit",
                  month: "2-digit",
                  year: "numeric",
                })}
              </h2>
              <p className="mt-1 text-crm-xs text-crm-text-3">
                {coordinating
                  ? "Yêu cầu cần điều phối trong workspace"
                  : "Công việc được giao cho tôi"}{" "}
                · Theo ngày phát sinh
              </p>
            </div>
            {query.isSuccess && <span className="text-crm-text-3">{items.length} công việc</span>}
          </div>
          {query.isPending ? (
            <TableSkeleton columns={columns.length} label="Đang tải công việc" />
          ) : query.error ? (
            <div className="p-4">
              <ErrorState error={query.error} />
              <Button variant="outline" onClick={() => void query.refetch()}>
                Thử lại
              </Button>
            </div>
          ) : items.length === 0 ? (
            <EmptyState icon={IconCalendarTime} title="Không có công việc trong ngày đã chọn." />
          ) : (
            <div
              className="min-w-0 max-w-full overflow-x-auto [&_button]:min-h-9 md:[&_button]:min-h-7"
              role="region"
              aria-label="Bảng công việc"
              tabIndex={0}
            >
              <DataTable
                columns={columns}
                rows={items}
                getRowKey={(task) => task.id}
                onRowClick={open}
              />
            </div>
          )}
        </section>
      </PageBody>
      <AssignTaskDialog task={assigning} onClose={() => setAssigning(null)} />
    </>
  );
};
