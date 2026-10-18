import test from 'node:test';
import assert from 'node:assert/strict';
import {ESLint} from 'eslint';

test('ESLint rejects a platform import of a domain application', async () => {
  const eslint = new ESLint();
  const [result] = await eslint.lintText('import {ticket} from "../../apps/servicedesk/ticket.js";', {
    filePath: 'src/platform/process/forbidden.js',
  });
  assert.ok(result.messages.some(message => message.ruleId === 'no-restricted-imports'));
});
