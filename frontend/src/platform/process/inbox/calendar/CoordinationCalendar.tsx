import { useState } from "react";
import { Link } from "react-router";
import { useQuery } from "@tanstack/react-query";
import { IconCalendarTime, IconRefresh } from "@tabler/icons-react";
import { cn } from "cn";
import { useCurrentWorkspace, usePeopleNames, personName } from "@/platform/human/workspace";
import type { WorkItem } from "@/platform/services";
import { getCoordinationWork } from "@/platform/services/workCalendarService";
import { useFallbackInterval } from "@/platform/shared/realtime";
import { formatDateTime } from "@/platform/shared/lib/format";
import { Button } from "@/platform/shared/ui/button";
import { EmptyState } from "@/platform/shared/ui/EmptyState";
import { ErrorState } from "@/platform/shared/ui/ErrorState";
import { Input } from "@/platform/shared/ui/input";
import { PageBody, PageToolbar } from "@/platform/shared/ui/page";
import { SelectField } from "@/platform/shared/ui/SelectField";
import { Tag } from "@/platform/shared/ui/Tag";
import { useProcessLabels } from "../../catalog/useProcessCatalog";
import { BusinessKeyLink } from "../../common/BusinessKeyLink";
import { inboxAction } from "../../common/inboxActions";
import { processLink } from "../../common/processLinks";
import { processKeys } from "../../common/queryKeys";
import { AssignTaskDialog } from "../../tasks/AssignTaskDialog";
import { useNow } from "../../work/useNow";
import { CalendarDays } from "./CalendarDays";
import { calendarDate } from "./calendarModel";
import { WorkAgendaSkeleton } from "./WorkAgenda";

type Bucket = "day" | "backlog" | "all";
const BUCKETS = { day: "Phát sinh trong ngày", backlog: "Tồn từ trước", all: "Tất cả đang mở" };
const normalize = (value: string) =>
  value
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/đ/gi, "d")
    .toLocaleLowerCase("vi");
const stageKey = (task: WorkItem) => `${task.processKey}/${task.key}`;

/** Coordination uses workspace inbox tasks and their creation dates, independent of Agent SLA queues. */
export const CoordinationCalendar = () => {
  const { current } = useCurrentWorkspace();
  const interval = useFallbackInterval(30_000);
  const labels = useProcessLabels();
  const now = useNow();
  const today = calendarDate(now);
  const [selected, setSelected] = useState<string | null>(null);
  const date = selected ?? today;
  const [bucket, setBucket] = useState<Bucket>("day");
  const [search, setSearch] = useState("");
  const [stage, setStage] = useState("");
  const [assignment, setAssignment] = useState("");
  const [assigning, setAssigning] = useState<WorkItem | null>(null);
  const query = useQuery({
    queryKey: [...processKeys.all(current), "coordination-calendar"],
    queryFn: ({ signal }) => getCoordinationWork(current!, signal),
    enabled: current !== null,
    retry: false,
    refetchInterval: interval,
  });
  const tasks = (query.isSuccess ? query.data : []).filter(
    (task) => task.candidateGroups.length > 0,
  );
  const names = usePeopleNames(tasks.map((task) => task.assignee));
  const term = normalize(search.trim());
  const stages = [
    ...new Map(
      tasks.map((task) => [
        stageKey(task),
        {
          value: stageKey(task),
          label: `${labels.taskName(task.processKey, task.key, task.name)} · ${labels.processName(task.processKey)}`,
        },
      ]),
    ).values(),
  ];
  const available = tasks.filter(
    (task) =>
      (!stage || stageKey(task) === stage) &&
      (!assignment || (assignment === "assigned" ? Boolean(task.assignee) : !task.assignee)) &&
      (!term ||
        normalize(
          `${task.businessKey} ${task.name} ${labels.taskName(task.processKey, task.key, task.name)} ${labels.processName(task.processKey)} ${task.assignee ? personName(names, task.assignee) : ""}`,
        ).includes(term)),
  );
  const matchesBucket = (task: WorkItem, value: Bucket) => {
    const created = calendarDate(new Date(task.createdAt));
    return value === "all" || (value === "day" ? created === date : created < date);
  };
  const items = available
    .filter((task) => matchesBucket(task, bucket))
    .sort((a, b) => Date.parse(a.createdAt) - Date.parse(b.createdAt) || a.id.localeCompare(b.id));
  const counts = new Map<string, number>();
  for (const task of available) {
    const day = calendarDate(new Date(task.createdAt));
    counts.set(day, (counts.get(day) ?? 0) + 1);
  }
  return (
    <>
      <PageToolbar
        start={<span className="text-crm-sm font-medium">Ngày phát sinh công việc</span>}
        end={
          <div className="flex w-full min-w-0 flex-wrap items-center gap-2">
            <Input
              aria-label="Tìm việc điều phối"
              placeholder="Tìm việc điều phối…"
              value={search}
              onChange={(event) => setSearch(event.target.value)}
              className="h-9 min-w-0 flex-1 sm:w-56 sm:flex-none md:h-7"
            />
            <SelectField
              aria-label="Bước công việc"
              placeholder="Mọi bước"
              options={stages}
              value={stage}
              onValueChange={setStage}
              className="max-w-full min-h-9 md:h-7"
            />
            <SelectField
              aria-label="Tình trạng giao việc"
              placeholder="Mọi tình trạng"
              value={assignment}
              onValueChange={setAssignment}
              options={[
                { value: "unassigned", label: "Chưa giao" },
                { value: "assigned", label: "Đã giao" },
              ]}
              className="min-h-9 md:h-7"
            />
            <Button
              variant="ghost"
              size="icon-sm"
              className="size-9 md:size-7"
              aria-label="Làm mới lịch điều phối"
              disabled={query.isFetching}
              onClick={() => void query.refetch()}
            >
              <IconRefresh size={16} />
            </Button>
          </div>
        }
      />
      <PageBody>
        <div className="flex w-full min-w-0 flex-col gap-5 p-4 lg:p-6">
          <CalendarDays
            title="Lịch điều phối"
            selected={date}
            today={today}
            counts={query.isSuccess ? counts : undefined}
            onSelect={(value) => {
              setSelected(value === today ? null : value);
              setBucket("day");
            }}
          />
          <p className="text-crm-sm text-crm-text-2">
            Việc còn mở trong workspace cần tiếp nhận, duyệt hoặc giao xử lý. Ngày trên lịch là ngày
            phát sinh bước công việc. Chọn “Tồn từ trước” để xem việc còn chờ từ các ngày trước.
          </p>
          <div
            role="group"
            aria-label="Nhóm việc điều phối"
            className="grid grid-cols-1 gap-2 sm:grid-cols-3"
          >
            {(["day", "backlog", "all"] as const).map((value) => (
              <button
                key={value}
                type="button"
                aria-pressed={bucket === value}
                onClick={() => setBucket(value)}
                className={cn(
                  "flex min-w-0 items-center justify-between gap-2 rounded-crm-md border p-3 text-left text-crm-sm outline-none focus-visible:ring-2 focus-visible:ring-crm-focus",
                  bucket === value
                    ? "border-crm-focus bg-crm-accent-soft text-crm-accent"
                    : "border-crm-line text-crm-text-2 hover:bg-crm-hover",
                )}
              >
                <span>{BUCKETS[value]}</span>
                <span className="text-crm-lg font-medium tabular-nums">
                  {query.isSuccess
                    ? available.filter((task) => matchesBucket(task, value)).length
                    : "—"}
                </span>
              </button>
            ))}
          </div>
          <section aria-label="Việc cần điều phối" className="min-w-0">
            <div className="mb-3 flex items-center justify-between gap-2 text-crm-sm">
              <h2 className="font-medium">{BUCKETS[bucket]}</h2>
              {query.isSuccess && (
                <span className="text-crm-text-3">{items.length} việc đang mở</span>
              )}
            </div>
            {query.isPending ? (
              <WorkAgendaSkeleton />
            ) : query.error ? (
              <div>
                <ErrorState error={query.error} />
                <Button variant="outline" onClick={() => void query.refetch()}>
                  Thử lại
                </Button>
              </div>
            ) : items.length === 0 ? (
              <EmptyState
                icon={IconCalendarTime}
                title="Không có việc điều phối phù hợp."
                description={
                  bucket === "day"
                    ? "Xem Tồn từ trước hoặc Tất cả đang mở để kiểm tra các việc đang chờ."
                    : undefined
                }
              />
            ) : (
              <div className="grid min-w-0 grid-cols-1 gap-3 xl:grid-cols-2">
                {items.map((task) => {
                  const Action = inboxAction(task.processKey);
                  const fallback = (
                    <Button size="sm" variant="info" onClick={() => setAssigning(task)}>
                      {task.assignee ? "Giao lại" : "Giao việc"}
                    </Button>
                  );
                  const title = labels.taskName(task.processKey, task.key, task.name);
                  return (
                    <article
                      key={task.id}
                      aria-label={`${title} ${task.businessKey}`}
                      className="min-w-0 rounded-crm-md border border-crm-line border-l-2 border-l-crm-accent bg-crm-surface p-4"
                    >
                      <div className="mb-2 flex flex-wrap items-center justify-between gap-2">
                        <Tag tone={task.assignee ? "blue" : "orange"}>
                          {task.assignee ? "Đã giao" : "Chưa giao"}
                        </Tag>
                        <time dateTime={task.createdAt} className="text-crm-xs text-crm-text-3">
                          Phát sinh: {formatDateTime(task.createdAt)}
                        </time>
                      </div>
                      <Link
                        to={
                          processLink(task.processKey, task.businessKey) ??
                          `/processes/${task.processInstanceId}`
                        }
                        className="font-medium text-crm-text hover:text-crm-accent"
                      >
                        {title}
                      </Link>
                      <div className="mt-2 flex min-w-0 flex-wrap items-center gap-x-2 gap-y-1 text-crm-sm text-crm-text-2">
                        <BusinessKeyLink
                          processKey={task.processKey}
                          businessKey={task.businessKey}
                        />
                        <span className="break-words">{labels.processName(task.processKey)}</span>
                      </div>
                      <div className="mt-3 flex flex-wrap items-center justify-between gap-3 border-t border-crm-line-light pt-3">
                        <span className="min-w-0 break-words text-crm-sm text-crm-text-2">
                          {task.assignee
                            ? `Đang giao: ${personName(names, task.assignee)}`
                            : "Chưa có người giữ việc"}
                        </span>
                        <div className="[&_button]:min-h-9 md:[&_button]:min-h-7">
                          {Action ? <Action task={task} fallback={fallback} /> : fallback}
                        </div>
                      </div>
                    </article>
                  );
                })}
              </div>
            )}
          </section>
        </div>
      </PageBody>
      <AssignTaskDialog task={assigning} onClose={() => setAssigning(null)} />
    </>
  );
};
