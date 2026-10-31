import { afterEach, describe, expect, it, vi } from "vitest";
import { AxiosHeaders, type AxiosAdapter } from "axios";
import { apiClient, configureHttpAuth } from "../../../src/platform/shared/api";
import {
  appendClarification,
  contextualAssessmentSchema,
  enrichmentStateSchema,
  getContextRevision,
} from "../../../src/apps/servicedesk/services/enrichmentService";

const id = "11111111-1111-4111-8111-111111111111";
const state = {
  contractVersion: "ticket-enrichment-lifecycle@0.1.0",
  ticketId: id,
  contextRevision: 1,
  status: "AWAITING_ANSWER",
  version: 2,
  roundCount: 1,
  manualReviewRequired: false,
  workflowGateApplied: false,
  needsReview: true,
  operationalAuthority: false,
  deadlineAt: null,
  remindedAt: null,
  requestInstanceId: null,
  deliveryStatus: "NOT_REQUESTED",
  nextAction: "ANSWER_CLARIFICATION",
  reasonCodes: [],
  questions: [],
  readiness: null,
};

afterEach(() => {
  apiClient.defaults.adapter = undefined;
});

describe("Service Desk enrichment contract", () => {
  it("does not accept workflow authority or an unrecognized lifecycle state", () => {
    expect(enrichmentStateSchema.parse(state).workflowGateApplied).toBe(false);
    expect(() => enrichmentStateSchema.parse({ ...state, operationalAuthority: true })).toThrow();
    expect(() => enrichmentStateSchema.parse({ ...state, status: "WORKFLOW_PAUSED" })).toThrow();
    expect(() => enrichmentStateSchema.parse({ ...state, assignedAgent: "untrusted" })).toThrow();
  });

  it("refuses invented numeric confidence in a contextual assessment", () => {
    const value = {
      contractVersion: "ticket-assessment-contextual@0.1.0",
      ticketId: id,
      contextRevision: 1,
      contextDigest: "a".repeat(64),
      assessmentStatus: "DETERMINISTIC_ONLY",
      dataReadiness: "READINESS_OK",
      ai07: {
        category: null,
        impact: "I3",
        urgency: null,
        priority: null,
        riskFlags: [],
        confidence: null,
        confidenceSemantics: "NOT_CALIBRATED",
      },
      evidence: [],
      reasons: [],
      provenance: null,
      needsReview: true,
      operationalAuthority: false,
      workflowGateApplied: false,
    };
    expect(contextualAssessmentSchema.parse(value).ai07?.confidence).toBeNull();
    expect(() =>
      contextualAssessmentSchema.parse({ ...value, ai07: { ...value.ai07, confidence: 0.99 } }),
    ).toThrow();
  });

  it("sends clarification to the existing ID with only the revision and user answer", async () => {
    configureHttpAuth({
      getAccessToken: async () => "synthetic",
      renewAccessToken: async () => null,
      onUnauthorized: vi.fn(),
    });
    let body: unknown;
    const adapter: AxiosAdapter = async (config) => {
      expect(config.url).toBe(`/apps/servicedesk/v1/workspaces/lab-a/tickets/${id}/clarifications`);
      body = JSON.parse(config.data as string);
      return {
        config,
        status: 201,
        statusText: "Created",
        headers: new AxiosHeaders(),
        data: {
          contractVersion: "ticket-clarification@0.1.0",
          ticketId: id,
          answerId: id,
          contextRevision: 2,
          assessmentStatus: "AI07_REFRESH_REQUIRED",
          replayed: false,
        },
      };
    };
    apiClient.defaults.adapter = adapter;
    const input = {
      contractVersion: "ticket-clarification@0.1.0" as const,
      clientAnswerId: id,
      expectedContextRevision: 1,
      answers: [{ slot: "device.identifier", value: "15", correction: false }],
    };
    await appendClarification("lab-a", id, input);
    expect(body).toEqual(input);
  });

  it("does not render a revision response belonging to another ticket", async () => {
    const adapter: AxiosAdapter = async (config) => ({
      config,
      status: 200,
      statusText: "OK",
      headers: new AxiosHeaders(),
      data: {
        contractVersion: "ticket-context-revision@0.1.0",
        ticketId: "22222222-2222-4222-8222-222222222222",
        contextRevision: 2,
      },
    });
    apiClient.defaults.adapter = adapter;
    await expect(getContextRevision("lab-a", id)).rejects.toThrow("Kết quả không thuộc ticket");
  });
});
