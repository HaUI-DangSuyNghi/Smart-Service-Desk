import { useCurrentWorkspace } from "../../../platform/human/workspace";
import type { RequestActionProps } from "../../../platform/process";
import { RequesterTicketAction } from "./RequesterTicketAction";

/** Nút trên dòng ở trang Yêu cầu chung; businessKey của quy trình service-desk là id ticket. */
export const TicketRequestAction = ({ businessKey, activeTaskKeys }: RequestActionProps) => {
  const { current } = useCurrentWorkspace();
  if (!current) return null;
  return (
    <RequesterTicketAction
      workspace={current}
      ticketId={businessKey}
      awaitingConfirmation={activeTaskKeys.includes("confirm")}
    />
  );
};
