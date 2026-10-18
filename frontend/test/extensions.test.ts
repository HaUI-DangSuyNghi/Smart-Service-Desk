import test from 'node:test';
import assert from 'node:assert/strict';
import {createExtensionRegistry} from '../src/platform/extensions/registry.ts';
import {createProcessClient} from '../src/platform/process/client.ts';

test('platform accepts a second domain through registration only', () => {
  const registry = createExtensionRegistry();
  registry.register({id: 'roomrequest', routes: [{path: '/rooms', page: 'room-list'}], processResolvers: {
    'room-request': key => `/rooms/${encodeURIComponent(key)}`,
  }});
  assert.equal(registry.resolveProcess('room-request', 'one/two'), '/rooms/one%2Ftwo');
  assert.equal(registry.resolveProcess('unknown', 'one'), null);
  assert.throws(() => registry.register({id: 'roomrequest'}));
  assert.throws(() => registry.register({id: 'other', processResolvers: {'room-request': () => ''}}));
  assert.equal(registry.list().length, 1);
});

test('process client uses the HPDI workspace contract and encodes identifiers', async () => {
  let called: string | undefined;
  const process = createProcessClient(async <T>(path: string): Promise<T> => {called = path; return {} as T;});
  await process.timeline('lab-a', 'id/one');
  assert.equal(called, '/api/hpdi/v1/workspaces/lab-a/instances/id%2Fone/timeline');
});
