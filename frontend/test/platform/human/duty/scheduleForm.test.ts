import { describe, expect, it } from "vitest";
import {
  nextShift,
  scheduleSchema,
  scheduleValues,
  toLocalInput,
  toSchedule,
} from "../../../../src/platform/human/duty/scheduleForm";
import type { AgentSchedule } from "../../../../src/platform/services";

const schedule: AgentSchedule = {
  subject: "agent-a",
  version: 4,
  enabled: true,
  shifts: [
    { startsAt: "2026-10-29T00:00:00.000Z", endsAt: "2026-10-29T08:00:00.000Z" },
    { startsAt: "2026-10-28T00:00:00.000Z", endsAt: "2026-10-28T08:00:00.000Z" },
  ],
};

const local = (iso: string) => toLocalInput(iso);

describe("scheduleForm", () => {
  it("đổi qua lại giữa ISO và ô datetime-local không lệch giờ", () => {
    const values = scheduleValues(schedule);
    // Ca được sắp theo thời gian
    expect(values.shifts[0].startsAt).toBe(local("2026-10-28T00:00:00.000Z"));
    expect(toSchedule(schedule, values)).toEqual({
      subject: "agent-a",
      version: 4,
      enabled: true,
      shifts: [
        { startsAt: "2026-10-28T00:00:00.000Z", endsAt: "2026-10-28T08:00:00.000Z" },
        { startsAt: "2026-10-29T00:00:00.000Z", endsAt: "2026-10-29T08:00:00.000Z" },
      ],
    });
  });

  it("chặn ca kết thúc trước khi bắt đầu, dài quá 24 giờ, hoặc chồng nhau", () => {
    const shift = (start: string, end: string) => ({ startsAt: local(start), endsAt: local(end) });
    const parse = (shifts: { startsAt: string; endsAt: string }[]) =>
      scheduleSchema.safeParse({ enabled: true, shifts }).success;
    expect(parse([shift("2026-10-28T08:00:00Z", "2026-10-28T07:00:00Z")])).toBe(false);
    expect(parse([shift("2026-10-28T00:00:00Z", "2026-10-29T01:00:00Z")])).toBe(false);
    expect(
      parse([
        shift("2026-10-28T00:00:00Z", "2026-10-28T08:00:00Z"),
        shift("2026-10-28T07:00:00Z", "2026-10-28T09:00:00Z"),
      ]),
    ).toBe(false);
    expect(
      parse([
        shift("2026-10-28T00:00:00Z", "2026-10-28T08:00:00Z"),
        shift("2026-10-28T08:00:00Z", "2026-10-28T16:00:00Z"),
      ]),
    ).toBe(true);
  });

  it("ca đang diễn ra hoặc gần nhất sắp tới", () => {
    expect(nextShift(schedule, new Date("2026-10-28T04:00:00Z"))?.startsAt).toBe(
      "2026-10-28T00:00:00.000Z",
    );
    expect(nextShift(schedule, new Date("2026-10-28T12:00:00Z"))?.startsAt).toBe(
      "2026-10-29T00:00:00.000Z",
    );
    expect(nextShift(schedule, new Date("2026-10-30T00:00:00Z"))).toBeNull();
  });
});
