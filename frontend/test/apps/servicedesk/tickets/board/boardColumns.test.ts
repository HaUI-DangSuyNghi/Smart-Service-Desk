import { describe, expect, it } from "vitest";
import type { TicketState } from "../../../../../src/apps/servicedesk/services/ticketService";
import {
  BOARD_COLUMNS,
  columnOf,
} from "../../../../../src/apps/servicedesk/tickets/board/boardColumns";
import { TICKET_STATE_LABELS } from "../../../../../src/apps/servicedesk/tickets/ticketLabels";

describe("boardColumns", () => {
  it("mọi trạng thái nằm đúng một cột", () => {
    for (const state of Object.keys(TICKET_STATE_LABELS) as TicketState[]) {
      const owners = BOARD_COLUMNS.filter((column) => column.states.includes(state));
      expect(owners, state).toHaveLength(1);
    }
  });

  it("không xác định gộp vào cột Chờ duyệt", () => {
    expect(columnOf("UNCLASSIFIED")?.key).toBe("review");
    expect(columnOf("AWAITING_REVIEW")?.key).toBe("review");
  });

  it("Mở lại gộp vào cột Đã giao", () => {
    expect(columnOf("REOPENED")?.key).toBe("assigned");
  });

  it("Đang tiếp nhận vào cột Chờ duyệt, Theo ticket gốc vào cột Đang chờ", () => {
    expect(columnOf("INTAKE")?.key).toBe("review");
    expect(columnOf("FOLLOWING")?.key).toBe("pending");
  });

  it("thứ tự cột theo luồng, Đã đóng ở cuối và thu gọn", () => {
    expect(BOARD_COLUMNS.map((column) => column.key)).toEqual([
      "review",
      "assigned",
      "inProgress",
      "pending",
      "confirm",
      "closed",
    ]);
    expect(BOARD_COLUMNS.at(-1)?.collapsedByDefault).toBe(true);
  });

  it("chưa có trạng thái (backend cũ) thì không thuộc cột nào", () => {
    expect(columnOf(undefined)).toBeUndefined();
  });
});
