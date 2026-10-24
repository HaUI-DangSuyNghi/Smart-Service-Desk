import { describe, expect, it } from "vitest";
import { personName, UNKNOWN_PERSON } from "../../../../src/platform/human/workspace/people";

describe("personName", () => {
  const names = new Map([["sub-1", "Demo Agent"]]);

  it("lấy họ tên theo danh bạ", () => {
    expect(personName(names, "sub-1")).toBe("Demo Agent");
  });

  it("không tra được (tài khoản cũ, danh bạ lỗi) thì hiện Không rõ tên, không hiện mã", () => {
    expect(personName(names, "0d56141f-e02b-42bf-80ef-1476dcfa580c")).toBe(UNKNOWN_PERSON);
  });
});
