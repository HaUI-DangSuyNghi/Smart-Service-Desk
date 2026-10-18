export function createProcessClient(request) {
  const base = (workspace) =>
    `/api/hpdi/v1/workspaces/${encodeURIComponent(workspace)}`;
  return Object.freeze({
    inbox: (workspace, scope = "CANDIDATE") =>
      request(`${base(workspace)}/inbox?scope=${encodeURIComponent(scope)}`),
    timeline: (workspace, id) =>
      request(
        `${base(workspace)}/instances/${encodeURIComponent(id)}/timeline`,
      ),
    claim: (workspace, id) =>
      request(`${base(workspace)}/tasks/${encodeURIComponent(id)}/claim`, {
        method: "POST",
      }),
    complete: (workspace, id, completion) =>
      request(`${base(workspace)}/tasks/${encodeURIComponent(id)}/complete`, {
        method: "POST",
        body: JSON.stringify(completion),
      }),
  });
}
