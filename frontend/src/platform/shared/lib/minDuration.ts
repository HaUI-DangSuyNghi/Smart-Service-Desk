/** Mọi loading hiện ít nhất chừng này, tránh chớp lên rồi tắt ngay. */
export const LOADING_MIN_MS = 600;

export const wait = (ms: number): Promise<void> => {
  return new Promise((resolve) => setTimeout(resolve, ms));
};

export const withMinDuration = async <T>(
  promise: Promise<T>,
  minMs = LOADING_MIN_MS,
): Promise<T> => {
  const [result] = await Promise.all([promise, wait(minMs)]);
  return result;
};
