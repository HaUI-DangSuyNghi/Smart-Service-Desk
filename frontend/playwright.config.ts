import { defineConfig, devices } from "@playwright/test";

// A dedicated port avoids accidentally testing a previously built Docker frontend.
const browserPort = Number(process.env.PLAYWRIGHT_PORT ?? "5175");
const browserOrigin = `http://127.0.0.1:${browserPort}`;

export default defineConfig({
  testDir: "./e2e",
  fullyParallel: true,
  forbidOnly: Boolean(process.env.CI),
  retries: 0,
  reporter: "list",
  use: {
    baseURL: browserOrigin,
    trace: "off",
    screenshot: "off",
    video: "off",
  },
  projects: [{ name: "chromium", use: { ...devices["Desktop Chrome"] } }],
  webServer: {
    command: `npm run dev -- --host 127.0.0.1 --port ${browserPort}`,
    url: browserOrigin,
    reuseExistingServer: false,
  },
});
