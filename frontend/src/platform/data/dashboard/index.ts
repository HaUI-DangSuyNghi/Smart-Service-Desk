// Dashboard (khối D): số liệu quy trình cho điều phối viên, app góp thêm khối số liệu qua registry
export { default as DashboardPage } from "./DashboardPage";
export { lastDays, type DateRange } from "./dashboardRange";
export {
  configureDashboardWidgets,
  type DashboardWidget,
  type DashboardWidgetProps,
} from "./dashboardWidgets";
export { StatTile } from "./StatTile";
