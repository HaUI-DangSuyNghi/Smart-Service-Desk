import { describe, expect, it } from "vitest";
import type { MenuItem } from "../../../../../src/platform/shell/router/registry";
import {
  activeChildIndex,
  isPathActive,
  subItemState,
  visibleMenu,
} from "../../../../../src/platform/shell/layout/sidebar/menuTree";

const menu: MenuItem[] = [
  { label: "Trang chủ", path: "/home" },
  {
    label: "Công việc",
    path: "/work",
    children: [
      { label: "Việc của tôi", path: "/inbox", requiredPermissions: ["task:handle"] },
      { label: "Ca trực", path: "/duty", requiredPermissions: ["task:assign"] },
    ],
  },
  {
    label: "Quản trị",
    path: "/administration",
    children: [{ label: "Nhật ký", path: "/audit", requiredPermissions: ["audit:view"] }],
  },
];

const allowed = (permissions: string[]) => (item: MenuItem) =>
  (item.requiredPermissions ?? []).every((permission) => permissions.includes(permission));

describe("visibleMenu", () => {
  it("nhóm chỉ giữ trang con được thấy, hết trang con thì ẩn cả nhóm", () => {
    const result = visibleMenu(menu, allowed(["task:handle"]));
    expect(result.map((item) => item.label)).toEqual(["Trang chủ", "Công việc"]);
    expect(result[1].children?.map((child) => child.path)).toEqual(["/inbox"]);
  });
});

describe("activeChildIndex", () => {
  const children: MenuItem[] = [
    { label: "Ticket", path: "/servicedesk/tickets" },
    { label: "Chờ duyệt", path: "/servicedesk/review" },
  ];

  it("trang chi tiết thuộc mục danh sách của nó", () => {
    expect(activeChildIndex(children, "/servicedesk/tickets/abc")).toBe(0);
    expect(activeChildIndex(children, "/servicedesk/review")).toBe(1);
    expect(activeChildIndex(children, "/home")).toBe(-1);
  });

  it("không nhầm đường dẫn chỉ trùng phần đầu", () => {
    expect(isPathActive("/servicedesk/tickets-old", "/servicedesk/tickets")).toBe(false);
  });

  it("nhiều mục khớp thì lấy mục khớp dài nhất", () => {
    const nested: MenuItem[] = [
      { label: "Gốc", path: "/roomrequest" },
      { label: "Con", path: "/roomrequest/requests" },
    ];
    expect(activeChildIndex(nested, "/roomrequest/requests/1")).toBe(1);
  });
});

describe("subItemState", () => {
  it("tô đậm đường từ nhóm xuống mục đang chọn, mục cuối không kéo đường dọc", () => {
    expect([0, 1, 2].map((index) => subItemState(index, 3, 1))).toEqual([
      "intermediate-before-selected",
      "intermediate-selected",
      "last-not-selected",
    ]);
    expect(subItemState(2, 3, 2)).toBe("last-selected");
    expect(subItemState(0, 3, -1)).toBe("intermediate-after-selected");
  });
});
