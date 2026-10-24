/** processKey + businessKey → đường dẫn trang chi tiết của app; null khi app không đăng ký. */
export type ProcessLinkResolver = (processKey: string, businessKey: string) => string | null;

let resolver: ProcessLinkResolver = () => null;

/**
 * Shell gọi một lần lúc khởi động (App.tsx) với `registry.processLink`.
 * Process không import shell được nên nhận qua hàm cấu hình, giống `configureHttpAuth`.
 */
export const configureProcessLinks = (value: ProcessLinkResolver) => {
  resolver = value;
};

export const processLink: ProcessLinkResolver = (processKey, businessKey) =>
  resolver(processKey, businessKey);
