import { describe, expect, it } from "vitest";
import { requestManagementSchema } from "../../../src/platform/services/requestLifecycleService";

const history = {
  id: "550e8400-e29b-41d4-a716-446655440000",
  version: 1,
  action: "UPDATED",
  actor: "requester",
  reason: "Correct description",
  beforeValues: { description: "Before" },
  afterValues: { description: "After" },
  createdAt: "2026-11-07T00:00:00Z",
};
const request = {
  processId: "process",
  version: "a".repeat(64),
  editable: true,
  cancellable: true,
  cancelled: false,
  explanation: "",
  fields: [],
  history: [history],
};

describe("request lifecycle history", () => {
  it("parses a nonempty change history", () => {
    const result = requestManagementSchema.parse(request);
    expect(result.history[0].beforeValues.description).toBe("Before");
    expect(result.history[0].afterValues.description).toBe("After");
  });

  it("rejects non-string history values", () => {
    expect(
      requestManagementSchema.safeParse({
        ...request,
        history: [{ ...history, afterValues: { description: 123 } }],
      }).success,
    ).toBe(false);
  });
});
