import { useState } from "react";
import { IconMessagePlus } from "@tabler/icons-react";
import { Button } from "../../../platform/shared/ui/button";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "../../../platform/shared/ui/dialog";
import { EnrichmentPanel } from "./EnrichmentPanel";

interface Props {
  workspace: string;
  ticketId: string;
  /** Ticket đang qua luồng tiếp nhận: thông tin bổ sung chờ kiểm tra trước khi duyệt. */
  managedIntake: boolean;
}

/**
 * Bổ sung thông tin cho ticket: trang chi tiết chỉ còn một nút gọn, bấm mới mở form
 * (câu hỏi của AI nếu có, hoặc tự chọn loại thông tin để thêm).
 */
export const EnrichmentDialog = ({ workspace, ticketId, managedIntake }: Props) => {
  const [open, setOpen] = useState(false);

  return (
    <div className="border-b border-crm-line px-4 py-3">
      <Dialog open={open} onOpenChange={setOpen}>
        <DialogTrigger asChild>
          <Button variant="outline" size="sm" className="h-9 md:h-7">
            <IconMessagePlus size={14} stroke={2} />
            Bổ sung thông tin
          </Button>
        </DialogTrigger>
        <DialogContent className="sm:max-w-130" aria-describedby={undefined}>
          <DialogHeader>
            <DialogTitle>Bổ sung thông tin</DialogTitle>
          </DialogHeader>
          {/* Chỉ tải khi mở: mỗi lần mở đọc trạng thái mới nhất */}
          {open && (
            <EnrichmentPanel
              workspace={workspace}
              ticketId={ticketId}
              managedIntake={managedIntake}
              embedded
            />
          )}
        </DialogContent>
      </Dialog>
    </div>
  );
};
