import type { Estimate } from "@/platform/services";
import { estimateBasisText, formatMinutes } from "./workLabels";

/** "1 giờ · Mặc định theo loại": số phút làm dự kiến và nguồn của con số (R2). */
export const EstimateText = ({ estimate }: { estimate: Estimate }) => {
  return (
    <span className="inline-flex flex-wrap items-baseline gap-x-1">
      <span>{formatMinutes(estimate.minutes)}</span>
      <span className="text-crm-sm text-crm-text-3">· {estimateBasisText(estimate)}</span>
    </span>
  );
};
