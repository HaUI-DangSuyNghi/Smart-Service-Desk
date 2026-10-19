export interface JsonRequest {
  <T = unknown>(path: string, options?: RequestInit): Promise<T>;
}
export interface JsonClientOptions {
  readonly fetch?: typeof globalThis.fetch;
  readonly accessToken: () => string | Promise<string>;
}

/** OIDC adapter uses Authorization Code + PKCE; tokens stay opaque to domain code. */
export function createJsonClient({fetch: request = globalThis.fetch, accessToken}: JsonClientOptions): JsonRequest {
  return async <T = unknown>(path: string, options: RequestInit = {}): Promise<T> => {
    if (!path.startsWith('/api/')) throw new Error('API path required');
    const token = await accessToken();
    if (!token) throw new Error('Access token required');
    const headers = new Headers(options.headers);
    headers.set('Content-Type', 'application/json');
    headers.set('Authorization', `Bearer ${token}`);
    const response = await request(path, {...options, headers});
    if (!response.ok) throw new Error(`Request failed (${response.status})`);
    return (response.status === 204 ? undefined : await response.json()) as T;
  };
}
