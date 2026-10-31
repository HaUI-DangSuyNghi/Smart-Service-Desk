import { useState } from "react";
import { IconChartBar } from "@tabler/icons-react";
import { Button } from "@/platform/shared/ui/button";

const DAY_LABEL = new Intl.DateTimeFormat("vi-VN", { day: "2-digit", month: "2-digit" });

// "2026-10-24" là ngày theo lịch, đọc theo giờ máy để không lệch ngày do múi giờ
const dayLabel = (date: string) => {
  const [year, month, day] = date.split("-").map(Number);
  return DAY_LABEL.format(new Date(year ?? 0, (month ?? 1) - 1, day ?? 1));
};

interface Props {
  daily: readonly { date: string; started: number }[];
}

/**
 * Cột yêu cầu mới theo ngày: một chuỗi số liệu nên một màu, không cần chú thích (tiêu đề đã nói).
 * Rê chuột hoặc focus vào cột để xem số; có nút chuyển sang dạng bảng cho người không xem được biểu đồ.
 */
export const DailyChart = ({ daily }: Props) => {
  const [showTable, setShowTable] = useState(false);
  const max = Math.max(1, ...daily.map((day) => day.started));
  const total = daily.reduce((sum, day) => sum + day.started, 0);
  const first = daily.at(0);
  const middle = daily.at(Math.floor(daily.length / 2));
  const last = daily.at(-1);

  return (
    <section className="flex min-w-0 flex-col gap-4 rounded-2xl border border-crm-tone-blue/15 bg-crm-surface p-4">
      <div className="flex items-center justify-between gap-2">
        <h2 className="flex items-center gap-2 font-semibold">
          <span className="flex size-8 shrink-0 items-center justify-center rounded-xl bg-crm-tone-blue-soft text-crm-tone-blue">
            <IconChartBar size={17} />
          </span>
          Yêu cầu mới theo ngày
        </h2>
        <Button variant="info" size="sm" onClick={() => setShowTable(!showTable)}>
          {showTable ? "Xem biểu đồ" : "Xem bảng"}
        </Button>
      </div>

      {showTable ? (
        <div className="max-h-64 overflow-auto">
          <table className="w-full text-left">
            <thead>
              <tr className="text-crm-text-3">
                <th className="py-1 font-normal">Ngày</th>
                <th className="py-1 text-right font-normal">Yêu cầu mới</th>
              </tr>
            </thead>
            <tbody>
              {daily.map((day) => (
                <tr key={day.date} className="border-t border-crm-line-light">
                  <td className="py-1">{dayLabel(day.date)}</td>
                  <td className="py-1 text-right tabular-nums">{day.started}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : (
        <div className="flex gap-2">
          {/* Trục dọc tối giản: chỉ mốc cao nhất và 0 */}
          <div className="flex h-40 flex-col justify-between text-right text-crm-xs text-crm-text-3 tabular-nums">
            <span>{max}</span>
            <span>0</span>
          </div>
          <div className="flex min-w-0 flex-1 flex-col gap-1">
            <div
              role="img"
              aria-label={`Biểu đồ yêu cầu mới theo ngày: tổng ${total}, ngày cao nhất ${max}`}
              className="flex h-40 items-end gap-0.5 border-b border-crm-line"
            >
              {daily.map((day) => (
                // Vùng rê chuột là cả cột (rộng hơn thanh) cho dễ trỏ
                <div
                  key={day.date}
                  tabIndex={0}
                  className="group relative flex h-full min-w-0 flex-1 items-end outline-none"
                >
                  <div
                    className="w-full rounded-t-crm-sm bg-linear-to-t from-crm-tone-blue to-crm-accent/65 transition-opacity duration-100 group-hover:opacity-80 group-focus:opacity-80"
                    style={{
                      height: `${(day.started / max) * 100}%`,
                      minHeight: day.started > 0 ? 2 : 0,
                    }}
                  />
                  <div className="pointer-events-none absolute bottom-full left-1/2 z-10 mb-1 hidden -translate-x-1/2 rounded-crm-sm bg-crm-text px-2 py-1 text-crm-xs whitespace-nowrap text-white shadow-crm-strong group-hover:block group-focus:block">
                    {dayLabel(day.date)}: {day.started} yêu cầu
                  </div>
                </div>
              ))}
            </div>
            {/* Nhãn trục ngang thưa (đầu, giữa, cuối) để không đè nhau trên màn hẹp */}
            <div className="flex justify-between text-crm-xs text-crm-text-3">
              <span>{first && dayLabel(first.date)}</span>
              <span>{middle && daily.length > 2 && dayLabel(middle.date)}</span>
              <span>{last && daily.length > 1 && dayLabel(last.date)}</span>
            </div>
          </div>
        </div>
      )}
    </section>
  );
};
