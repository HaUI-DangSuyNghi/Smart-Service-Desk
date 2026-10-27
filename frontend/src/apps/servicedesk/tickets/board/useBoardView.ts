import { useSearchParams } from "react-router";

export type ListMode = "table" | "board";

const STORAGE_KEY = "servicedesk.tickets.view";

const readStored = () => {
  try {
    return localStorage.getItem(STORAGE_KEY);
  } catch {
    return null;
  }
};

const writeStored = (view: ListMode) => {
  try {
    localStorage.setItem(STORAGE_KEY, view);
  } catch {
    // Chặn lưu trữ (cửa sổ riêng tư...) thì chỉ mất việc nhớ lựa chọn
  }
};

/** URL thắng nơi lưu; không được xem Board thì luôn là Bảng. */
export const resolveView = (
  fromUrl: string | null,
  stored: string | null,
  allowed: boolean,
): ListMode => (allowed && (fromUrl ?? stored) === "board" ? "board" : "table");

/** Chế độ Bảng/Board của trang danh sách: nhớ trong URL (chia sẻ link) và trình duyệt. */
export const useBoardView = (allowed: boolean) => {
  const [params, setParams] = useSearchParams();
  const view = resolveView(params.get("view"), readStored(), allowed);

  const setView = (next: ListMode) => {
    writeStored(next);
    setParams(
      (previous) => {
        const updated = new URLSearchParams(previous);
        if (next === "board") updated.set("view", "board");
        else updated.delete("view");
        return updated;
      },
      { replace: true },
    );
  };

  return [view, setView] as const;
};
