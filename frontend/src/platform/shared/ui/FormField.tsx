import type { ReactNode } from "react";
import { Label } from "./label";

interface Props {
  /** id của ô nhập để bấm nhãn thì focus vào ô. */
  id: string;
  label: string;
  error?: string;
  children: ReactNode;
}

/** Nhãn + ô nhập + dòng lỗi, dùng trong mọi form. */
export const FormField = ({ id, label, error, children }: Props) => {
  return (
    <div className="flex flex-col gap-1">
      <Label htmlFor={id} className="text-crm-sm font-normal text-crm-text-2">
        {label}
      </Label>
      {children}
      {error && <p className="text-crm-sm text-crm-danger">{error}</p>}
    </div>
  );
};
