import { apiClient } from "../../../platform/shared/api";
import { parseSuggestion, type Suggestion } from "../../../platform/intelligence";

/** Quy trình gắn với yêu cầu đặt phòng (khớp RoomProcessView). */
export interface RoomProcess {
  processInstanceId: string;
  processKey: string;
  businessKey: string;
  workspace: string;
}

/** Khớp RoomRequest trong contracts/apps/roomrequest/openapi.yaml. */
export interface RoomRequest {
  id: string;
  workspace: string;
  /** Subject người tạo. */
  creator: string;
  room: string;
  knowledgeId: string | null;
  process: RoomProcess;
}

const requestsPath = (workspace: string) =>
  `/apps/roomrequest/v1/workspaces/${encodeURIComponent(workspace)}/requests`;

/** Tạo yêu cầu đặt phòng, backend khởi tạo luôn quy trình duyệt. */
export const createRoomRequest = async (workspace: string, room: string): Promise<RoomRequest> => {
  const { data } = await apiClient.post<RoomRequest>(requestsPath(workspace), { room });
  return data;
};

/** Một yêu cầu; 404 khi không tồn tại hoặc người xem không được thấy. */
export const getRoomRequest = async (
  workspace: string,
  id: string,
  signal?: AbortSignal,
): Promise<RoomRequest> => {
  const { data } = await apiClient.get<RoomRequest>(
    `${requestsPath(workspace)}/${encodeURIComponent(id)}`,
    { signal },
  );
  return data;
};

/** Gợi ý của AI theo profile room-request; không gửi body (có body là 400). */
export const suggestRoom = async (workspace: string, id: string): Promise<Suggestion> => {
  const { data } = await apiClient.post<unknown>(
    `${requestsPath(workspace)}/${encodeURIComponent(id)}/suggestion`,
  );
  return parseSuggestion(data);
};
