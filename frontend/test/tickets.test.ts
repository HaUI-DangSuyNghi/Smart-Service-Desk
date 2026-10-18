import test from 'node:test';
import assert from 'node:assert/strict';
import {createTicketClient} from '../src/apps/servicedesk/api/tickets.ts';
import {createJsonClient} from '../src/platform/auth/json-client.ts';

test('ticket search and statistics preserve the same query and time filters', async () => {
  const paths: string[] = [];
  const client = createTicketClient(async <T>(path: string): Promise<T> => {paths.push(path); return {} as T;});
  const filter = {q: 'Wi-Fi & VPN', from: '2026-10-01T00:00:00Z', to: '2026-10-19T00:00:00Z'};
  await client.list('lab/a', {...filter, page: 2, size: 10});
  await client.statistics('lab/a', filter);
  const [searchPath, statsPath] = paths;
  assert.ok(searchPath && statsPath);
  const search = new URL(searchPath, 'http://localhost');
  const stats = new URL(statsPath, 'http://localhost');
  assert.equal(search.pathname, '/api/apps/servicedesk/v1/workspaces/lab%2Fa/tickets');
  assert.equal(stats.pathname, search.pathname + '/statistics');
  for (const [key, value] of Object.entries(filter)) {
    assert.equal(search.searchParams.get(key), value);
    assert.equal(stats.searchParams.get(key), value);
  }
  assert.equal(search.searchParams.get('page'), '2');
  assert.equal(search.searchParams.get('size'), '10');
});

test('progress and bodyless triage use persisted ticket identifiers', async () => {
  const calls: {path: string; options: RequestInit | undefined}[] = [];
  const client = createTicketClient(async <T>(path: string, options?: RequestInit): Promise<T> => {
    calls.push({path, options});
    return {} as T;
  });
  await client.progress('lab-a', 'ticket/one');
  await client.triage('lab-a', 'ticket/one');
  assert.deepEqual(calls, [
    {path: '/api/apps/servicedesk/v1/workspaces/lab-a/tickets/ticket%2Fone/progress', options: undefined},
    {path: '/api/apps/servicedesk/v1/workspaces/lab-a/tickets/ticket%2Fone/triage', options: {method: 'POST'}},
  ]);
});

test('a failed triage response remains an error rather than fabricated advice', async () => {
  const client = createTicketClient(createJsonClient({
    accessToken: () => 'opaque-test-token',
    fetch: async () => new Response('{}', {status: 503}),
  }));
  await assert.rejects(client.triage('lab-a', 'one'), /Request failed \(503\)/);
});
