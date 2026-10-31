import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { createKeyBatcher } from "../../../../src/platform/shared/realtime/keyBatcher";

describe("createKeyBatcher", () => {
  beforeEach(() => vi.useFakeTimers());
  afterEach(() => vi.useRealTimers());

  it("gom sự kiện trong khoảng chờ, mỗi khóa chỉ làm mới một lần", () => {
    const flush = vi.fn();
    const batcher = createKeyBatcher(flush, 500);
    batcher.add([["tickets", "a"]]);
    batcher.add([["tickets", "a"], ["process", "a"]]);
    vi.advanceTimersByTime(499);
    expect(flush).not.toHaveBeenCalled();
    vi.advanceTimersByTime(1);
    expect(flush).toHaveBeenCalledTimes(1);
    expect(flush).toHaveBeenCalledWith([
      ["tickets", "a"],
      ["process", "a"],
    ]);
  });

  it("sự kiện không cần làm mới gì thì không hẹn giờ", () => {
    const flush = vi.fn();
    createKeyBatcher(flush, 500).add([]);
    vi.runAllTimers();
    expect(flush).not.toHaveBeenCalled();
  });

  it("huỷ (rời trang) thì bỏ phần đang chờ", () => {
    const flush = vi.fn();
    const batcher = createKeyBatcher(flush, 500);
    batcher.add([["tickets", "a"]]);
    batcher.cancel();
    vi.runAllTimers();
    expect(flush).not.toHaveBeenCalled();
  });
});
