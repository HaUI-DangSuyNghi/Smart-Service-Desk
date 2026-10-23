import { useCallback, useSyncExternalStore } from "react";

/** true khi media query khớp; tự cập nhật khi đổi cỡ màn hình hoặc xoay máy. */
export const useMediaQuery = (query: string) => {
  // useSyncExternalStore thay cho useState + useEffect: không setState trong effect, không nháy lần đầu
  const subscribe = useCallback(
    (onChange: () => void) => {
      const list = window.matchMedia(query);
      list.addEventListener("change", onChange);
      return () => list.removeEventListener("change", onChange);
    },
    [query],
  );
  return useSyncExternalStore(subscribe, () => window.matchMedia(query).matches);
};

/** Màn nhỏ hơn mốc `md` của Tailwind (48rem = 768px): sidebar bị khóa thu gọn. */
export const useIsMobile = () => useMediaQuery("(width < 48rem)");
