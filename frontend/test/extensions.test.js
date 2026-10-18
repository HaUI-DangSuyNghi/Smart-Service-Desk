import test from 'node:test';
import assert from 'node:assert/strict';
import {createExtensionRegistry} from '../src/platform/extensions/registry.js';
import {createProcessClient} from '../src/platform/process/client.js';

test('platform accepts a second domain through registration only', () => {
  const registry = createExtensionRegistry();
  registry.register({id: 'roomrequest', routes: [{path: '/rooms'}], processResolvers: {
    'room-request': key => `/rooms/${encodeURIComponent(key)}`,
  }});
  assert.equal(registry.resolveProcess('room-request', 'one/two'), '/rooms/one%2Ftwo');
  assert.equal(registry.resolveProcess('unknown', 'one'), null);
  assert.throws(() => registry.register({id: 'roomrequest'}));
  assert.throws(() => registry.register({id: 'other', processResolvers: {'room-request': () => ''}}));
  assert.equal(registry.list().length, 1);
});

test('process client uses the HPDI workspace contract and encodes identifiers', async () => {
  let called;
  const process = createProcessClient(async path => {called = path; return {};});
  await process.timeline('lab-a', 'id/one');
  assert.equal(called, '/api/hpdi/v1/workspaces/lab-a/instances/id%2Fone/timeline');
});
