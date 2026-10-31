import type { ReactNode } from "react";
import { Label } from "./label";
import { Tooltip } from "./Tooltip";

interface Props {
  /** id của ô nhập để bấm nhãn thì focus vào ô. */
  id: string;
  label: string;
  /** Giải thích ngắn hiện khi rê chuột vào nhãn (nhãn có gạch chấm). */
  hint?: string;
  error?: string;
  children: ReactNode;
}

/** Nhãn + ô nhập + dòng lỗi, dùng trong mọi form. */
export const FormField = ({ id, label, hint, error, children }: Props) => {
  return (
    // gap-2: nhãn không dính sát ô nhập
    <div className="flex flex-col gap-2">
      {hint ? (
        <Tooltip content={hint}>
          <Label htmlFor={id} className="w-fit text-crm-sm font-normal text-crm-text-2">
            {label}
          </Label>
        </Tooltip>
      ) : (
        <Label htmlFor={id} className="text-crm-sm font-normal text-crm-text-2">
          {label}
        </Label>
      )}
      {children}
      {error && <p className="text-crm-sm text-crm-danger">{error}</p>}
    </div>
  );
};
