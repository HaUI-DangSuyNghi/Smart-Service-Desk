import type { ComponentType } from "react";

/** Dữ liệu một dòng yêu cầu đưa cho nút thao tác của app. */
export interface RequestActionProps {
  processKey: string;
  businessKey: string;
  /** Bước đang mở của quy trình. */
  activeTaskKeys: readonly string[];
}

/** processKey → nút thao tác của app trên dòng yêu cầu; null khi app không đăng ký. */
export type RequestActionResolver = (
  processKey: string,
) => ComponentType<RequestActionProps> | null;

let resolver: RequestActionResolver = () => null;

/** Shell gọi một lần lúc khởi động (App.tsx) với `registry.requestAction`, giống `configureProcessLinks`. */
export const configureRequestActions = (value: RequestActionResolver) => {
  resolver = value;
};

export const requestAction: RequestActionResolver = (processKey) => resolver(processKey);
