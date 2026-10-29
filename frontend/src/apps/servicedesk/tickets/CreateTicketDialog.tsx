import { useRef, useState } from "react";
import { useNavigate } from "react-router";
import { IconPlus, IconSparkles } from "@tabler/icons-react";
import { Button } from "../../../platform/shared/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "../../../platform/shared/ui/dialog";
import { toast } from "../../../platform/shared/ui/toast";
import { SmartTicketForm } from "../report/SmartTicketForm";
import type { TicketView } from "../services/ticketService";
import { ticketCode } from "./ticketLabels";

/**
 * Nút "Tạo ticket" + form gửi yêu cầu kiểu trợ lý (R0): gửi xong AI hỏi thêm phần còn thiếu,
 * đủ thì tự đóng. Đóng giữa chừng thì yêu cầu vẫn đã được gửi.
 */
export const CreateTicketDialog = () => {
  const navigate = useNavigate();
  const [open, setOpen] = useState(false);
  // Ticket đã gửi nhưng còn câu hỏi thêm: đóng form giữa chừng vẫn báo đã gửi
  const unfinished = useRef<TicketView | null>(null);

  const announce = (ticket: TicketView) =>
    toast.success(`Đã gửi yêu cầu ${ticketCode(ticket.id)}`, {
      description: "Điều phối viên sẽ duyệt và giao người xử lý.",
      action: { label: "Xem", onClick: () => navigate(`/servicedesk/tickets/${ticket.id}`) },
    });

  const handleOpenChange = (next: boolean) => {
    if (!next && unfinished.current) {
      announce(unfinished.current);
      unfinished.current = null;
    }
    setOpen(next);
  };

  const handleFinished = (ticket: TicketView) => {
    unfinished.current = null;
    announce(ticket);
    setOpen(false);
  };

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogTrigger asChild>
        <Button size="sm" aria-label="Tạo ticket">
          <IconPlus size={14} stroke={2} />
          {/* Điện thoại chỉ giữ dấu + để tiêu đề trang còn chỗ */}
          <span className="hidden sm:inline">Tạo ticket</span>
        </Button>
      </DialogTrigger>
      <DialogContent className="sm:max-w-130">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-1.5">
            <IconSparkles size={16} stroke={2} className="text-crm-accent" />
            Gửi yêu cầu hỗ trợ
          </DialogTitle>
          <DialogDescription>Trợ lý sẽ hỏi thêm nếu còn thiếu thông tin để xử lý nhanh hơn.</DialogDescription>
        </DialogHeader>
        {/* Mỗi lần mở là một hội thoại mới, có mã gửi mới */}
        {open && (
          <SmartTicketForm
            onSent={(ticket) => {
              unfinished.current = ticket;
            }}
            onFinished={handleFinished}
          />
        )}
      </DialogContent>
    </Dialog>
  );
};
