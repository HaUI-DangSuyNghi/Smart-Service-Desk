import { describe, expect, it } from "vitest";
import type { SchedulePolicy } from "../../../../src/platform/services";
import {
  addDays,
  slotLockReason,
  weekDays,
  weekStart,
  zonedNow,
} from "../../../../src/platform/human/duty/scheduleRules";

const policy: SchedulePolicy = {
  zone: "Asia/Ho_Chi_Minh",
  shifts: [
    { name: "Morning", start: "07:00:00", end: "11:30:00" },
    { name: "Afternoon", start: "13:00:00", end: "17:00:00" },
  ],
  cutoff: "07:00:00",
  weekendRegistration: false,
  approvalRequired: false,
  today: "2026-11-05",
  holidays: [{ date: "2026-11-06", name: "Lễ thử" }],
};
// Thứ Năm 05/11/2026
const thursday = (time: string) => ({ date: "2026-11-05", time });

describe("scheduleRules", () => {
  it("ngày lễ khoá như ngày nghỉ, kèm ghi chú tên lễ", () => {
    expect(slotLockReason(policy, "2026-11-06", "11:30:00", false, thursday("06:00"))).toMatch(
      /Lễ thử/,
    );
    expect(
      slotLockReason(
        { ...policy, weekendRegistration: true },
        "2026-11-06",
        "11:30:00",
        false,
        thursday("06:00"),
      ),
    ).toBeNull();
  });

  it("tuần bắt đầu Thứ Hai, kể cả khi đang là Chủ nhật", () => {
    expect(weekStart("2026-11-05")).toBe("2026-11-02");
    expect(weekStart("2026-11-08")).toBe("2026-11-02");
    expect(weekDays("2026-11-02")).toHaveLength(7);
    expect(addDays("2026-12-31", 1)).toBe("2027-01-01");
  });

  it("lấy ngày giờ theo múi giờ nghiệp vụ", () => {
    expect(zonedNow("Asia/Ho_Chi_Minh", new Date("2026-11-04T23:30:00Z"))).toEqual({
      date: "2026-11-05",
      time: "06:30",
    });
  });

  it("kỹ thuật viên: chỉ trước 07:00 của chính ngày đó", () => {
    // Thứ 5 không đăng ký Thứ 4
    expect(slotLockReason(policy, "2026-11-04", "11:30:00", false, thursday("06:00"))).toMatch(
      /đã qua/,
    );
    // 06:30 Thứ 5 vẫn đăng ký được ca Sáng, Chiều Thứ 5
    expect(slotLockReason(policy, "2026-11-05", "11:30:00", false, thursday("06:30"))).toBeNull();
    // Đang trong ca Sáng hoặc chiều Thứ 5 thì không đăng ký Thứ 5 nữa
    expect(slotLockReason(policy, "2026-11-05", "11:30:00", false, thursday("09:00"))).toMatch(
      /07:00/,
    );
    expect(slotLockReason(policy, "2026-11-05", "17:00:00", false, thursday("14:00"))).toMatch(
      /07:00/,
    );
    expect(slotLockReason(policy, "2026-11-09", "11:30:00", false, thursday("14:00"))).toBeNull();
  });

  it("T7, CN theo cấu hình; điều phối viên đăng ký hộ tới hết ca", () => {
    expect(slotLockReason(policy, "2026-11-07", "11:30:00", false, thursday("06:00"))).toMatch(
      /Thứ 7/,
    );
    expect(
      slotLockReason(
        { ...policy, weekendRegistration: true },
        "2026-11-07",
        "11:30:00",
        false,
        thursday("06:00"),
      ),
    ).toBeNull();
    expect(slotLockReason(policy, "2026-11-05", "17:00:00", true, thursday("14:00"))).toBeNull();
    expect(slotLockReason(policy, "2026-11-05", "11:30:00", true, thursday("14:00"))).toMatch(
      /kết thúc/,
    );
  });
});
