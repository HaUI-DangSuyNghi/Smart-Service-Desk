export function createTicketClient(request) {
  const base = (workspace) =>
    `/api/apps/servicedesk/v1/workspaces/${encodeURIComponent(workspace)}/tickets`;
  return Object.freeze({
    list: (workspace, query = "") =>
      request(`${base(workspace)}?q=${encodeURIComponent(query)}`),
    get: (workspace, id) =>
      request(`${base(workspace)}/${encodeURIComponent(id)}`),
    create: (workspace, input) =>
      request(base(workspace), { method: "POST", body: JSON.stringify(input) }),
    statistics: (workspace) => request(`${base(workspace)}/statistics`),
  });
}
