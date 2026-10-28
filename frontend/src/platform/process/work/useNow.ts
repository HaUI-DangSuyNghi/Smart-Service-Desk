import { useEffect, useState } from "react";

const MINUTE_MS = 60 * 1000;

/** Thời điểm hiện tại, tự cập nhật mỗi phút để đồng hồ hạn xử lý đếm lùi. */
export const useNow = () => {
  const [now, setNow] = useState(() => Date.now());

  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), MINUTE_MS);
    return () => clearInterval(timer);
  }, []);

  return now;
};
