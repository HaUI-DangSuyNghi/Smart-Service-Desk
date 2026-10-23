/** Đường dẫn gốc theo workspace; mã hóa tên workspace để không chèn được ký tự lạ vào URL. */
export const coreWorkspace = (workspace: string) =>
  `/core/v1/workspaces/${encodeURIComponent(workspace)}`;

export const hpdiWorkspace = (workspace: string) =>
  `/hpdi/v1/workspaces/${encodeURIComponent(workspace)}`;
