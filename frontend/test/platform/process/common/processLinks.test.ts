import { afterEach, describe, expect, it } from "vitest";
import {
  configureProcessLinks,
  processLink,
} from "../../../../src/platform/process/common/processLinks";

describe("processLinks", () => {
  afterEach(() => configureProcessLinks(() => null));

  it("chưa cấu hình thì không có link", () => {
    expect(processLink("service-desk", "42")).toBeNull();
  });

  it("dùng hàm shell truyền vào", () => {
    configureProcessLinks((key, businessKey) => (key === "demo" ? `/demo/${businessKey}` : null));
    expect(processLink("demo", "42")).toBe("/demo/42");
    expect(processLink("other", "42")).toBeNull();
  });
});
