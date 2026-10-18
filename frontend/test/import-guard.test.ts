import test from 'node:test';
import assert from 'node:assert/strict';
import {ESLint} from 'eslint';

test('ESLint rejects a platform import of a domain application', async () => {
  const eslint = new ESLint();
  const [result] = await eslint.lintText('import {ticket} from "../../apps/servicedesk/ticket.ts";', {
    filePath: 'src/platform/process/forbidden.ts',
  });
  assert.ok(result);
  assert.ok(result.messages.some(message => message.ruleId === '@typescript-eslint/no-restricted-imports'));
});
