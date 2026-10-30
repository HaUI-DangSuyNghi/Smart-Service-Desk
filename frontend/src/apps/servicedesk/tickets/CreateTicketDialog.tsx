import { useState } from "react";
import { Controller, useForm, useWatch } from "react-hook-form";
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
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "../../../platform/shared/ui/dialog";
import { FormField } from "../../../platform/shared/ui/FormField";
import { Input } from "../../../platform/shared/ui/input";
import { SelectField } from "../../../platform/shared/ui/SelectField";
import { Textarea } from "../../../platform/shared/ui/textarea";
import { toast, toastError } from "../../../platform/shared/ui/toast";
import { appendClarification, type ClarificationInput } from "../services/enrichmentService";
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
  // Các câu dưới không bắt buộc; giá trị khớp đúng mã backend đọc được (context-slots)
  room: z
    .string()
    .trim()
    .refine(
      (value) => !value || /^[a-z]?\d{2,6}(?:[.-][0-9a-z]{1,6})?$/i.test(value),
      "Nhập số phòng, ví dụ 1304 hoặc A201",
    ),
  scope: z.enum(["", "INDIVIDUAL", "MULTIPLE", "WHOLE_ROOM"]),
  work: z.enum(["", "BLOCKED", "WORKAROUND"]),
});

type FormValues = z.infer<typeof schema>;

// Phạm vi → mức ảnh hưởng (I1/I2/I3) theo contextual-input-policy
const SCOPE_OPTIONS = [
  { value: "INDIVIDUAL", label: "Một người hoặc một máy" },
  { value: "MULTIPLE", label: "Nhiều người hoặc nhiều máy" },
  { value: "WHOLE_ROOM", label: "Cả phòng" },
];
// Dừng việc → độ gấp cao (U3); có cách khác → độ gấp thấp (U1)
const WORK_OPTIONS = [
  { value: "BLOCKED", label: "Bị dừng, không làm tiếp được" },
  { value: "WORKAROUND", label: "Vẫn làm được bằng cách khác" },
];
const FORM_SELECT = "h-9 w-full max-w-none sm:h-8";

/** Câu trả lời sẵn → thông tin bổ sung có cấu trúc; bộ luật dùng để tính ảnh hưởng, độ gấp, ưu tiên. */
const toAnswers = ({ room, scope, work }: FormValues): ClarificationInput["answers"] => [
  ...(room ? [{ slot: "location.room", value: room.toUpperCase(), correction: false }] : []),
  ...(scope ? [{ slot: "affectedScope", value: scope, correction: false }] : []),
  ...(work === "BLOCKED" ? [{ slot: "workBlocked", value: "true", correction: false }] : []),
  ...(work === "WORKAROUND"
    ? [
        { slot: "workBlocked", value: "false", correction: false },
        { slot: "workaroundAvailable", value: "true", correction: false },
      ]
    : []),
];

const CreateTicketForm = ({ onDone }: { onDone: () => void }) => {
  const navigate = useNavigate();
  const create = useCreateTicket();
  // Một mã cho mỗi lần mở form: bấm gửi lại sau lỗi mạng không tạo ticket trùng (R0)
  const [clientRequestId] = useState(() => crypto.randomUUID());
  const form = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { title: "", description: "", room: "", scope: "", work: "" },
  });
  const [title, description] = useWatch({ control: form.control, name: ["title", "description"] });
  const { errors } = form.formState;

  const handleSubmit = form.handleSubmit((values) => {
    const { title, description } = values;
    create.mutate(
      { clientRequestId, title, description },
      {
        onSuccess: async (ticket) => {
          const answers = toAnswers(values);
          if (answers.length) {
            // Ticket đã tạo: lỗi lưu câu trả lời không chặn, người gửi bổ sung lại ở trang chi tiết
            await appendClarification(ticket.workspace, ticket.id, {
              contractVersion: "ticket-clarification@0.1.0",
              clientAnswerId: crypto.randomUUID(),
              expectedContextRevision: 0,
              answers,
            }).catch(() =>
              toast.warning("Chưa lưu được thông tin thêm", {
                description: "Bạn có thể bổ sung lại ở trang chi tiết ticket.",
              }),
            );
          }
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
        {/* Không bắt buộc; điện thoại xếp dọc */}
        <div className="grid gap-3 sm:grid-cols-2">
          <FormField id="ticket-room" label="Phòng" error={errors.room?.message}>
            <Input
              id="ticket-room"
              maxLength={16}
              placeholder="Ví dụ: 1304"
              {...form.register("room")}
            />
          </FormField>
          <FormField id="ticket-scope" label="Phạm vi ảnh hưởng">
            <Controller
              control={form.control}
              name="scope"
              render={({ field }) => (
                <SelectField
                  aria-label="Phạm vi ảnh hưởng"
                  placeholder="Chưa rõ"
                  options={SCOPE_OPTIONS}
                  value={field.value}
                  onValueChange={field.onChange}
                  className={FORM_SELECT}
                />
              )}
            />
          </FormField>
          <div className="sm:col-span-2">
            <FormField id="ticket-work" label="Công việc của bạn">
              <Controller
                control={form.control}
                name="work"
                render={({ field }) => (
                  <SelectField
                    aria-label="Công việc của bạn"
                    placeholder="Chưa rõ"
                    options={WORK_OPTIONS}
                    value={field.value}
                    onValueChange={field.onChange}
                    className={FORM_SELECT}
                  />
                )}
              />
            </FormField>
          </div>
        </div>
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
      <DialogContent className="sm:max-w-120" aria-describedby={undefined}>
        {/* Mỗi lần mở là một form mới, có mã gửi mới */}
        {open && <CreateTicketForm onDone={() => setOpen(false)} />}
      </DialogContent>
    </Dialog>
  );
};
