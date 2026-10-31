import { IconRoute } from "@tabler/icons-react";
import { useProcessLabels } from "@/platform/process";
import type { ProcessCount } from "@/platform/services";
import { formatSeconds } from "@/platform/shared/lib/format";
import { DataTable, type DataTableColumn } from "@/platform/shared/ui/DataTable";

// Cột số canh phải để dễ so sánh hàng với hàng
const number = (value: number) => <span className="block text-right tabular-nums">{value}</span>;

/** Bảng số liệu theo từng quy trình, cùng khoảng thời gian với các thẻ phía trên. */
export const ProcessStatsTable = ({ processes }: { processes: readonly ProcessCount[] }) => {
  const labels = useProcessLabels();
  const columns: readonly DataTableColumn<ProcessCount>[] = [
    {
      key: "process",
      header: "Quy trình",
      icon: IconRoute,
      width: "220px",
      cell: (row) => labels.processName(row.processKey),
    },
    { key: "started", header: "Mới", width: "90px", cell: (row) => number(row.started) },
    { key: "running", header: "Đang chạy", width: "110px", cell: (row) => number(row.running) },
    {
      key: "completed",
      header: "Đã kết thúc",
      width: "110px",
      cell: (row) => number(row.completed),
    },
    { key: "waiting", header: "Chờ nhận", width: "100px", cell: (row) => number(row.waitingClaim) },
    {
      key: "progress",
      header: "Đang xử lý",
      width: "110px",
      cell: (row) => number(row.inProgress),
    },
    {
      key: "average",
      header: "Xử lý TB",
      width: "140px",
      cell: (row) =>
        row.averageCompletionSeconds === null ? (
          <span className="text-crm-text-3">—</span>
        ) : (
          formatSeconds(row.averageCompletionSeconds)
        ),
    },
  ];

  return (
    <section className="min-w-0 overflow-hidden rounded-2xl border border-crm-tone-turquoise/20 bg-crm-surface">
      <h2 className="flex items-center gap-2 border-b border-crm-line-light bg-linear-to-r from-crm-tone-turquoise-soft/60 to-crm-surface px-4 py-3 font-semibold">
        <span className="flex size-8 items-center justify-center rounded-xl bg-crm-tone-turquoise-soft text-crm-tone-turquoise">
          <IconRoute size={17} />
        </span>
        Theo quy trình
      </h2>
      <div className="overflow-x-auto">
        <DataTable columns={columns} rows={processes} getRowKey={(row) => row.processKey} />
      </div>
    </section>
  );
};
