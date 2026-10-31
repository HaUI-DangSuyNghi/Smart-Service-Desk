import type { ReactNode } from "react";

interface Props {
  title: string;
  children?: ReactNode;
}

/** Thông báo chiếm cả trang: đang tải, đang chuyển hướng, lỗi đăng nhập. */
export const FullPageMessage = ({ title, children }: Props) => {
  return (
    <div className="flex min-h-svh flex-col items-center justify-center gap-4 p-6 text-center">
      <p className="text-lg font-medium">{title}</p>
      {children}
    </div>
  );
};
