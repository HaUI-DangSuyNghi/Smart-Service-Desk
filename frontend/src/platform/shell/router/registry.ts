import type { ComponentType } from "react";
import type { DashboardWidget } from "@/platform/data/dashboard";
import type { RequestActionProps } from "@/platform/process";

/** Một trang của app, path tương đối với gốc, ví dụ "servicedesk/tickets". */
export interface AppRoute {
  readonly path: string;
  readonly component: ComponentType;
  /** Cần ít nhất một vai trò này ở workspace đang chọn; thiếu thì hiện trang 403. */
  readonly requiredRoles?: readonly string[];
  /** Cần ít nhất một quyền này (Keycloak, client dx-permissions); thiếu thì hiện trang 403. */
  readonly requiredPermissions?: readonly string[];
}

/** Icon cho menu; khớp component của @tabler/icons-react. */
export type MenuIcon = ComponentType<{ size?: number; stroke?: number; className?: string }>;

/** Màu icon menu (giống Twenty: mỗi mục một màu). */
export type MenuTone = "gray" | "blue" | "red" | "green" | "orange" | "purple" | "turquoise";

export interface MenuItem {
  readonly label: string;
  readonly path: string;
  readonly icon?: MenuIcon;
  readonly tone?: MenuTone;
  /** Chỉ hiện mục menu khi có ít nhất một vai trò này. */
  readonly requiredRoles?: readonly string[];
  /** Chỉ hiện mục menu khi có ít nhất một quyền này. */
  readonly requiredPermissions?: readonly string[];
  /**
   * Có thì mục này là nhóm xổ ra kiểu Twenty: bấm để mở/đóng (mở thì vào trang con đầu tiên),
   * path chỉ làm khóa và gom trang con. Mục con không lồng tiếp.
   */
  readonly children?: readonly MenuItem[];
  /** Nhóm mở sẵn khi vào app (người dùng vẫn đóng được). */
  readonly defaultOpen?: boolean;
  /** Component số đếm cạnh mục menu (tự lấy dữ liệu), ví dụ số việc đang chờ ở trang Công việc. */
  readonly badge?: ComponentType;
}

/** Một nhóm cấu hình riêng của app trên màn Cấu hình hệ thống (ví dụ bảng ước tính của Service Desk). */
export interface SettingsSection {
  readonly id: string;
  readonly title: string;
  readonly component: ComponentType;
}

/** Những gì một app (Tầng 3) đăng ký vào platform (Tầng 2). */
export interface AppModule {
  readonly id: string;
  readonly routes: readonly AppRoute[];
  readonly menu?: readonly MenuItem[];
  /** processKey → đường dẫn tới trang chi tiết của app, dùng cho inbox/timeline chung. */
  readonly processLinks?: Readonly<Record<string, (businessKey: string) => string>>;
  /** processKey → nút thao tác của app trên dòng ở trang Yêu cầu chung (ví dụ người gửi xác nhận). */
  readonly requestActions?: Readonly<Record<string, ComponentType<RequestActionProps>>>;
  /** Khối số liệu của app hiện trên dashboard chung (ví dụ thống kê ticket). */
  readonly dashboardWidgets?: readonly DashboardWidget[];
  /** Cấu hình riêng của app, hiện dưới phần cấu hình chung. */
  readonly settingsSections?: readonly SettingsSection[];
}

export interface AppRegistry {
  register: (app: AppModule) => void;
  apps: () => readonly AppModule[];
  menu: () => readonly MenuItem[];
  processLink: (processKey: string, businessKey: string) => string | null;
  requestAction: (processKey: string) => ComponentType<RequestActionProps> | null;
  dashboardWidgets: () => readonly DashboardWidget[];
  settingsSections: () => readonly SettingsSection[];
}

export const createAppRegistry = (): AppRegistry => {
  const modules: AppModule[] = [];
  const paths = new Set<string>();
  const links = new Map<string, (businessKey: string) => string>();

  return {
    register: (app) => {
      if (!app.id || modules.some((m) => m.id === app.id)) {
        throw new Error(`App id trống hoặc bị trùng: "${app.id}"`);
      }
      for (const route of app.routes) {
        if (paths.has(route.path)) throw new Error(`Route bị trùng: "${route.path}"`);
      }
      for (const key of Object.keys(app.processLinks ?? {})) {
        if (links.has(key)) throw new Error(`processKey đã có app khác đăng ký: "${key}"`);
      }
      app.routes.forEach((route) => paths.add(route.path));
      Object.entries(app.processLinks ?? {}).forEach(([key, link]) => links.set(key, link));
      modules.push(app);
    },
    apps: () => [...modules],
    menu: () => modules.flatMap((m) => m.menu ?? []),
    processLink: (processKey, businessKey) => links.get(processKey)?.(businessKey) ?? null,
    requestAction: (processKey) =>
      modules.find((m) => m.requestActions?.[processKey])?.requestActions?.[processKey] ?? null,
    dashboardWidgets: () => modules.flatMap((m) => m.dashboardWidgets ?? []),
    settingsSections: () => modules.flatMap((m) => m.settingsSections ?? []),
  };
};
