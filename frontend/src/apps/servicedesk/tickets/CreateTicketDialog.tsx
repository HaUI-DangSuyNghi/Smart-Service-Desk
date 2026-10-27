import { useState } from "react";
import { useForm, useWatch } from "react-hook-form";
import { useNavigate } from "react-router";
import { zodResolver } from "@hookform/resolvers/zod";
import { IconPlus } from "@tabler/icons-react";
import { z } from "zod";
import { ApiError } from "../../../platform/shared/api";
import { submitOnEnter } from "../../../platform/shared/lib/submitOnEnter";
import { Button } from "../../../platform/shared/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "../../../platform/shared/ui/dialog";
import { FormField } from "../../../platform/shared/ui/FormField";
import { Input } from "../../../platform/shared/ui/input";
import { Textarea } from "../../../platform/shared/ui/textarea";
import { toast, toastError } from "../../../platform/shared/ui/toast";
import { MAX_DESCRIPTION, MAX_TITLE } from "./ticketRules";
import { useCreateTicket } from "./useTickets";

// Khớp NewTicket: không để trống, tiêu đề không xuống dòng
const schema = z.object({
  title: z
    .string()
    .trim()
    .min(1, "Nhập tiêu đề")
    .max(MAX_TITLE, `Tối đa ${MAX_TITLE} ký tự`)
    .refine((value) => !/[\r\n\t]/.test(value), "Tiêu đề viết trên một dòng"),
  description: z
    .string()
    .trim()
    .min(1, "Mô tả sự cố")
    .max(MAX_DESCRIPTION, `Tối đa ${MAX_DESCRIPTION} ký tự`),
});

type FormValues = z.infer<typeof schema>;

const CreateTicketForm = ({ onDone }: { onDone: () => void }) => {
  const navigate = useNavigate();
  const create = useCreateTicket();
  // Một mã cho mỗi lần mở form: bấm gửi lại sau lỗi mạng không tạo ticket trùng (R0)
  const [clientRequestId] = useState(() => crypto.randomUUID());
  const form = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { title: "", description: "" },
  });
  const [title, description] = useWatch({ control: form.control, name: ["title", "description"] });
  const { errors } = form.formState;

  const handleSubmit = form.handleSubmit(({ title, description }) => {
    create.mutate(
      { clientRequestId, title, description },
      {
        onSuccess: (ticket) => {
          toast.success("Đã gửi yêu cầu", {
            description: "AI đang phân loại, điều phối viên sẽ duyệt và giao việc.",
          });
          onDone();
          navigate(`/servicedesk/tickets/${ticket.id}`);
        },
        onError: (error) => {
          // 409: lần gửi trước đã thành công (mất phản hồi) nên không gửi lại nữa
          if (error instanceof ApiError && error.status === 409) {
            toast.info("Yêu cầu này đã được gửi trước đó.");
            onDone();
            return;
          }
          toastError("Không gửi được yêu cầu.", error);
        },
      },
    );
  });

  return (
    <>
      <DialogHeader>
        <DialogTitle>Gửi yêu cầu hỗ trợ</DialogTitle>
        <DialogDescription>
          Ghi rõ phòng, thiết bị và mức ảnh hưởng để được xử lý nhanh hơn.
        </DialogDescription>
      </DialogHeader>

      <form
        id="create-ticket"
        onSubmit={handleSubmit}
        onKeyDown={submitOnEnter}
        className="flex flex-col gap-3"
      >
        <FormField id="ticket-title" label="Tiêu đề" error={errors.title?.message}>
          <Input
            id="ticket-title"
            maxLength={MAX_TITLE}
            placeholder="Ví dụ: Máy 15 phòng 1304 không lên nguồn"
            {...form.register("title")}
          />
          <span className="self-end text-crm-xs text-crm-text-3 tabular-nums">
            {title.length}/{MAX_TITLE}
          </span>
        </FormField>
        <FormField id="ticket-description" label="Mô tả" error={errors.description?.message}>
          <Textarea
            id="ticket-description"
            rows={5}
            maxLength={MAX_DESCRIPTION}
            placeholder="Sự cố ở đâu, ảnh hưởng mấy máy, đang bị chặn việc gì..."
            {...form.register("description")}
          />
          <span className="self-end text-crm-xs text-crm-text-3 tabular-nums">
            {description.length}/{MAX_DESCRIPTION}
          </span>
        </FormField>
      </form>

      <DialogFooter>
        <Button variant="outline" size="sm" onClick={onDone}>
          Hủy
        </Button>
        {/* Khóa nút khi đang gửi để chặn gửi trùng */}
        <Button type="submit" form="create-ticket" size="sm" disabled={create.isPending}>
          {create.isPending ? "Đang gửi..." : "Gửi yêu cầu"}
        </Button>
      </DialogFooter>
    </>
  );
};

/** Nút "Tạo ticket" + dialog gửi yêu cầu (R0). */
export const CreateTicketDialog = () => {
  const [open, setOpen] = useState(false);

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button size="sm" aria-label="Tạo ticket">
          <IconPlus size={14} stroke={2} />
          {/* Điện thoại chỉ giữ dấu + để tiêu đề trang còn chỗ */}
          <span className="hidden sm:inline">Tạo ticket</span>
        </Button>
      </DialogTrigger>
      <DialogContent className="sm:max-w-120">
        {/* Mỗi lần mở là một form mới, có mã gửi mới */}
        {open && <CreateTicketForm onDone={() => setOpen(false)} />}
      </DialogContent>
    </Dialog>
  );
};
