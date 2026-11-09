const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { createRequire } = require('node:module');
const { chromium, expect } = createRequire(path.resolve('frontend/package.json'))('@playwright/test');

async function main() {
  const [envFile, outputFile] = process.argv.slice(2);
  assert(envFile && outputFile, 'Usage: node scripts/acceptance/staging-browser.cjs <env-file> <report.json>');
  const settings = Object.fromEntries(fs.readFileSync(envFile, 'utf8').split(/\r?\n/)
    .filter(line => /^[A-Z_]+=/.test(line)).map(line => [line.slice(0, line.indexOf('=')), line.slice(line.indexOf('=') + 1)]));
  assert(settings.DEMO_PASSWORD, 'Missing DEMO_PASSWORD');
  const browser = await chromium.launch();
  const cases = [];
  try {
    for (const username of ['requester1', 'agent1', 'admin1']) {
      const context = await browser.newContext();
      try {
        const page = await context.newPage();
        page.setDefaultTimeout(45000);
        const pageErrors = [];
        let pkce = false;
        page.on('pageerror', error => pageErrors.push(error.message));
        page.on('request', request => {
          const url = new URL(request.url());
          if (url.pathname.endsWith('/protocol/openid-connect/auth') && url.searchParams.get('prompt') !== 'none') {
            pkce ||= url.searchParams.get('code_challenge_method') === 'S256' && Boolean(url.searchParams.get('code_challenge'));
          }
        });
        const meResponse = page.waitForResponse(response => response.url().endsWith('/api/core/v1/me') && response.status() === 200, { timeout: 60000 });
        meResponse.catch(() => {});
        await page.goto('http://localhost:5173/home');
        await page.locator('#username').fill(username);
        await page.locator('#password').fill(settings.DEMO_PASSWORD);
        await page.locator('#kc-login').click();
        const response = await meResponse;
        const me = await response.json();
        assert.equal(me.username, username);
        assert(pkce, 'Browser did not request S256 PKCE');
        await expect(page).toHaveURL(/localhost:5173\/home/);
        const restored = page.waitForResponse(reply => reply.url().endsWith('/api/core/v1/me') && reply.status() === 200);
        restored.catch(() => {});
        await page.reload();
        assert.equal((await (await restored).json()).username, username);
        await expect(page.locator('#username')).toHaveCount(0);
        const persistedTokens = await page.evaluate(() => [...Object.values(localStorage), ...Object.values(sessionStorage)]
          .some(value => /"(?:access_token|refresh_token|id_token)"\s*:/.test(value)));
        assert.equal(persistedTokens, false, 'Tokens persisted in browser storage');
        if (username === 'requester1') {
          await page.goto('http://localhost:5173/servicedesk/tickets');
          await page.getByRole('button', { name: 'T\u1ea1o ticket', exact: true }).first().click();
          const title = `Browser staging check ${Date.now()}`;
          await page.locator('#ticket-title').fill(title);
          await page.locator('#ticket-description').fill('Synthetic network issue for isolated staging verification.');
          const created = page.waitForResponse(reply => reply.request().method() === 'POST'
            && reply.url().endsWith('/workspaces/lab-a/tickets'));
          created.catch(() => {});
          await page.locator('button[form="create-ticket"][type="submit"]').click();
          assert.equal((await created).status(), 201, 'Ticket creation failed');
          await expect(page).toHaveURL(/\/servicedesk\/tickets\/[0-9a-f-]+$/);
          await expect(page.getByText(title, { exact: true }).first()).toBeVisible();
          cases.push({ username, status: 'PASS', checks: ['real-browser-create-ticket', 'ticket-detail'] });
        }
        await page.locator('[data-slot="dropdown-menu-trigger"]').first().click();
        await page.getByRole('menuitem', { name: '\u0110\u0103ng xu\u1ea5t', exact: true }).click();
        await expect(page.locator('#username')).toBeVisible({ timeout: 60000 });
        assert.deepEqual(pageErrors, [], 'Uncaught browser exceptions');
        cases.push({ username, status: 'PASS', checks: ['real-login-S256', 'me', 'reload-SSO', 'no-persisted-tokens', 'logout', 'no-page-errors'] });
        console.log(`PASS real Chromium session: ${username}`);
      } finally { await context.close(); }
    }
    fs.mkdirSync(path.dirname(outputFile), { recursive: true });
    fs.writeFileSync(outputFile, JSON.stringify({ status: 'PASS', cases }, null, 2) + '\n');
  } finally { await browser.close(); }
}

main().catch(error => { console.error(error.message); process.exitCode = 1; });
