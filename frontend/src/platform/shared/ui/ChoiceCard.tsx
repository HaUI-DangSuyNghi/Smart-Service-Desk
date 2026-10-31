import type { ComponentProps, ReactNode } from "react";

interface Props extends Omit<ComponentProps<"input">, "type"> {
  children: ReactNode;
}

/**
 * Một lựa chọn dạng thẻ (radio) cho nhóm ít lựa chọn: chọn kết quả, chọn quy trình.
 * Dùng radio thật nên bàn phím, trình đọc màn hình và react-hook-form `register` chạy sẵn.
 */
export const ChoiceCard = ({ children, ...input }: Props) => {
  return (
    <label className="flex min-h-9 cursor-pointer items-center gap-2 rounded-crm-sm border border-crm-line px-3 py-1.5 text-crm-text transition-colors duration-100 hover:bg-crm-hover has-checked:border-crm-accent has-checked:bg-crm-accent-soft has-focus-visible:border-crm-focus">
      <input type="radio" className="accent-crm-accent" {...input} />
      <span className="min-w-0 flex-1">{children}</span>
    </label>
  );
};
