import { apiClient } from "../../../platform/shared/api";
import { parseSuggestion, type Suggestion } from "../../../platform/intelligence";
import { listRequests, type ProcessSummary, type RequestScope } from "../../../platform/services";

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

export type RoomRequestRow = ProcessSummary & { room: string };

/** Filter after traversing all pages so other apps cannot hide room bookings. */
export const listRoomRequests = async (
  workspace: string,
  scope: RequestScope,
  signal?: AbortSignal,
): Promise<RoomRequestRow[]> => {
  const first = await listRequests(workspace, scope, { page: 0, size: 100 }, signal);
  const summaries = [...first.items];
  const size = first.size > 0 ? first.size : 100;
  for (let page = 1; page * size < first.total; page += 1) {
    const next = await listRequests(workspace, scope, { page, size }, signal);
    summaries.push(...next.items);
  }
  const rooms = [
    ...new Map(
      summaries.filter((item) => item.processKey === "room-request").map((item) => [item.id, item]),
    ).values(),
  ];
  const result: RoomRequestRow[] = [];
  for (let offset = 0; offset < rooms.length; offset += 10) {
    const batch = await Promise.all(
      rooms.slice(offset, offset + 10).map(async (item) => ({
        ...item,
        room: (await getRoomRequest(workspace, item.businessKey, signal)).room,
      })),
    );
    result.push(...batch);
  }
  return result;
};

/** Gợi ý của AI theo profile room-request; không gửi body (có body là 400). */
export const suggestRoom = async (workspace: string, id: string): Promise<Suggestion> => {
  const { data } = await apiClient.post<unknown>(
    `${requestsPath(workspace)}/${encodeURIComponent(id)}/suggestion`,
  );
  return parseSuggestion(data);
};
