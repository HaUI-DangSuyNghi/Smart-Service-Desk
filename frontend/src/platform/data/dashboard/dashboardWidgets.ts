import type { ComponentType } from "react";
import type { DateRange } from "./dashboardRange";

/** Khoảng thời gian dashboard đang chọn, truyền xuống widget của app. */
export type DashboardWidgetProps = DateRange;

/**
 * Một khối số liệu do app (Tầng 3) đóng góp vào dashboard chung, ví dụ thống kê ticket của Service Desk.
 * Widget tự gọi API của app; dashboard chỉ cấp khung và khoảng thời gian.
 */
export interface DashboardWidget {
  readonly id: string;
  readonly title: string;
  readonly component: ComponentType<DashboardWidgetProps>;
  /** Chỉ hiện khi người xem có ít nhất một quyền này. */
  readonly requiredPermissions?: readonly string[];
}

let widgets: readonly DashboardWidget[] = [];

/**
 * Shell gọi một lần lúc khởi động (App.tsx) với widget các app đã đăng ký.
 * Khối D không import shell được nên nhận qua hàm cấu hình, giống `configureProcessLinks`.
 */
export const configureDashboardWidgets = (value: readonly DashboardWidget[]) => {
  widgets = value;
};

export const dashboardWidgets = (): readonly DashboardWidget[] => widgets;
