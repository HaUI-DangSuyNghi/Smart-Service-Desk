import { type EffectCallback } from "react";
import { renderToStaticMarkup } from "react-dom/server";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { User } from "oidc-client-ts";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { AuthProvider } from "../../../../src/platform/human/auth/AuthProvider";
import { restoreSession } from "../../../../src/platform/human/auth/userManager";

const harness = vi.hoisted(() => ({
  effects: [] as EffectCallback[],
  listeners: new Map<string, (user?: User) => void>(),
  updates: vi.fn(),
}));

vi.mock("react", async (original) => ({
  ...(await original<typeof import("react")>()),
  useState: (initial: unknown) => [initial, harness.updates],
  useEffect: (effect: EffectCallback) => {
    harness.effects.push(effect);
  },
}));

vi.mock("../../../../src/platform/human/auth/userManager", () => ({
  getUserManager: () => ({
    events: Object.fromEntries(
      ["UserLoaded", "UserUnloaded", "AccessTokenExpired", "SilentRenewError"].flatMap((name) => [
        [`add${name}`, (handler: (user?: User) => void) => harness.listeners.set(name, handler)],
        [`remove${name}`, () => harness.listeners.delete(name)],
      ]),
    ),
  }),
  restoreSession: vi.fn(),
}));

let client: QueryClient;
let cleanups: ReturnType<EffectCallback>[];
const loaded = (subject: string) =>
  harness.listeners.get("UserLoaded")?.({ profile: { sub: subject } } as User);

const mount = (pathname = "/auth/callback") => {
  vi.stubGlobal("window", { location: { pathname } });
  client = new QueryClient();
  harness.effects = [];
  renderToStaticMarkup(
    <QueryClientProvider client={client}>
      <AuthProvider>
        <span />
      </AuthProvider>
    </QueryClientProvider>,
  );
  cleanups = harness.effects.map((effect) => effect());
};

beforeEach(() => {
  harness.updates.mockClear();
  vi.mocked(restoreSession).mockReset();
  mount();
});

afterEach(() => {
  cleanups.forEach((cleanup) => cleanup?.());
  client.clear();
  vi.unstubAllGlobals();
});

describe("authentication cache isolation", () => {
  it.each(["UserUnloaded", "AccessTokenExpired", "SilentRenewError", "UserLoaded"])(
    "ignores a late restoration after %s",
    async (event) => {
      cleanups.forEach((cleanup) => cleanup?.());
      let finish!: (user: User | null) => void;
      const pending = new Promise<User | null>((resolve) => {
        finish = resolve;
      });
      vi.mocked(restoreSession).mockReturnValue(pending);
      mount("/home");
      if (event === "UserLoaded") loaded("second-user");
      else harness.listeners.get(event)?.();
      harness.updates.mockClear();
      finish({ profile: { sub: "old-user" } } as User);
      await pending;
      expect(harness.updates).not.toHaveBeenCalled();
    },
  );

  it("ends the loading state when restoring storage fails", async () => {
    cleanups.forEach((cleanup) => cleanup?.());
    vi.mocked(restoreSession).mockRejectedValue(new Error("Storage unavailable"));
    mount("/home");
    await vi.waitFor(() => expect(harness.updates).toHaveBeenCalledWith("anonymous"));
  });

  it("clears cached user and ticket data when the identity changes", () => {
    loaded("first-user");
    client.setQueryData(["me"], { subject: "first-user" });
    client.setQueryData(["tickets", "workspace"], ["private-ticket"]);
    loaded("second-user");
    expect(client.getQueryCache().getAll()).toHaveLength(0);
  });

  it.each(["UserUnloaded", "AccessTokenExpired", "SilentRenewError"])(
    "clears cache on %s",
    (event) => {
      loaded("first-user");
      client.setQueryData(["me"], { subject: "first-user" });
      harness.listeners.get(event)?.();
      expect(client.getQueryCache().getAll()).toHaveLength(0);
    },
  );

  it("preserves cache when the same user's token is renewed", () => {
    loaded("first-user");
    client.setQueryData(["me"], { subject: "first-user" });
    loaded("first-user");
    expect(client.getQueryData(["me"])).toEqual({ subject: "first-user" });
  });
});
