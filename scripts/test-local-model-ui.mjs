// Headless smoke of the deployed frontend. Uses only the synthetic fixture ticket ID.
import { createRequire } from "node:module";
import { readFileSync, mkdirSync } from "node:fs";
import { fileURLToPath } from "node:url";

const require = createRequire(new URL("../frontend/package.json", import.meta.url));
const { chromium } = require("@playwright/test");
const ticketId = process.argv[2];
const askApi = process.argv.includes("--ask");
if (!/^[a-f0-9-]{36}$/.test(ticketId ?? "")) {
  throw new Error("Usage: node scripts/test-local-model-ui.mjs <synthetic-ticket-UUID>");
}
const config = Object.fromEntries(readFileSync(new URL("../.env", import.meta.url), "utf8")
  .replace(/^\uFEFF/, "").split(/\r?\n/).filter(line => /^[A-Z0-9_]+=/.test(line))
  .map(line => [line.slice(0, line.indexOf("=")), line.slice(line.indexOf("=") + 1)]));
let browser;
let stage = "browser startup";
try {
  browser = await chromium.launch({ headless: true });
  const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } });
  stage = "demo coordinator login";
  await page.goto(`http://localhost:5173/servicedesk/tickets/${ticketId}`);
  await page.locator("#username").fill("user3");
  await page.locator("#password").fill(config.DEMO_PASSWORD);
  await page.locator("#kc-login").click();
  await page.waitForURL(url => url.origin === "http://localhost:5173" && !url.pathname.startsWith("/auth/"), { timeout: 30000 });
  stage = "stored hybrid advice";
  await page.goto(`http://localhost:5173/servicedesk/tickets/${ticketId}`);
  const heading = page.getByRole("heading", { name: /Gợi ý của AI/ });
  await heading.waitFor({ state: "visible", timeout: 30000 });
  const text = await heading.locator("..").innerText();
  if (!text.includes("Model + bộ luật") || !text.includes("Bộ luật:") || !text.includes("Phần cứng")) {
    throw new Error("Hybrid advice missing");
  }
  if (askApi) {
    stage = "API question answering";
    await page.getByLabel("Bạn cần hỗ trợ điều gì?", { exact: true }).fill("Máy không lên nguồn, tôi nên kiểm tra gì trước?");
    await page.getByRole("button", { name: "Gửi câu hỏi", exact: true }).click();
    await page.getByText("Gợi ý từ model · Chưa đối chiếu tài liệu nội bộ", { exact: true }).waitFor({ timeout: 25000 });
    if (!(await page.locator("blockquote").innerText()).trim()) throw new Error("Answer missing");
    if (await page.getByRole("heading", { name: "Nguồn trích dẫn", exact: true }).count()) throw new Error("Fabricated citations");
  }
  mkdirSync(new URL("../.local/", import.meta.url), { recursive: true });
  await page.screenshot({ path: fileURLToPath(new URL(askApi ? "../.local/api-qa-ui.png" : "../.local/local-model-ui.png", import.meta.url)), fullPage: true });
  console.log(JSON.stringify({ result: "PASS", coordinatorLogin: true, hybridAdviceVisible: true, modelAndRulesVisible: true, ...(askApi ? { apiAnswerVisible: true, fabricatedCitations: false } : {}) }));
} catch {
  console.error("Local AI UI verification failed at: " + stage);
  process.exitCode = 1;
} finally {
  await browser?.close();
}
