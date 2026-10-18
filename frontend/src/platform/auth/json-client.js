/** Tokens come from an OIDC adapter using Authorization Code + PKCE, never domain input. */
export function createJsonClient({
  fetch: request = globalThis.fetch,
  accessToken,
}) {
  return async (path, options = {}) => {
    if (!path.startsWith("/api/")) throw new Error("API path required");
    const token = await accessToken();
    const response = await request(path, {
      ...options,
      headers: {
        ...options.headers,
        "Content-Type": "application/json",
        Authorization: `Bearer ${token}`,
      },
    });
    if (!response.ok) throw new Error(`Request failed (${response.status})`);
    return response.status === 204 ? undefined : response.json();
  };
}
