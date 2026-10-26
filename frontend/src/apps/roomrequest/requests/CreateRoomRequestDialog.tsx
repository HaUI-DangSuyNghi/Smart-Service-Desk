import { useState } from "react";
import { useForm } from "react-hook-form";
import { useNavigate } from "react-router";
import { zodResolver } from "@hookform/resolvers/zod";
import { IconPlus } from "@tabler/icons-react";
import { z } from "zod";
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
import { toast, toastError } from "../../../platform/shared/ui/toast";
import { useCreateRoomRequest } from "./useRoomRequests";

// Cùng luật với backend: 1–50 ký tự, không ký tự điều khiển
const MAX_ROOM_LENGTH = 50;
const schema = z.object({
  room: z
    .string()
    .trim()
    .min(1, "Nhập tên phòng")
    .max(MAX_ROOM_LENGTH, `Tối đa ${MAX_ROOM_LENGTH} ký tự`)
    // eslint-disable-next-line no-control-regex
    .regex(/^[^\u0000-\u001f\u007f-\u009f]*$/, "Tên phòng chứa ký tự không hợp lệ"),
});

type FormValues = z.infer<typeof schema>;

/** Nút "Đặt phòng" + form một ô; tạo xong chuyển sang trang chi tiết yêu cầu. */
export const CreateRoomRequestDialog = () => {
  const [open, setOpen] = useState(false);
  const navigate = useNavigate();
  const create = useCreateRoomRequest();
  const form = useForm<FormValues>({ resolver: zodResolver(schema), defaultValues: { room: "" } });
  const { errors } = form.formState;

  const handleOpenChange = (next: boolean) => {
    setOpen(next);
    form.reset({ room: "" });
    create.reset();
  };

  const handleSubmit = form.handleSubmit(({ room }) => {
    create.mutate(room, {
      onSuccess: (request) => {
        toast.success("Đã gửi yêu cầu đặt phòng", { description: request.room });
        handleOpenChange(false);
        navigate(`/roomrequest/requests/${request.id}`);
      },
      onError: (error) => toastError("Không gửi được yêu cầu đặt phòng.", error),
    });
  });

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogTrigger asChild>
        <Button size="sm">
          <IconPlus size={14} stroke={2} />
          Đặt phòng
        </Button>
      </DialogTrigger>

      <DialogContent className="sm:max-w-100">
        <DialogHeader>
          <DialogTitle>Đặt phòng</DialogTitle>
          <DialogDescription>Điều phối viên sẽ duyệt; theo dõi ở mục Yêu cầu.</DialogDescription>
        </DialogHeader>

        <form
          id="create-room-request"
          onSubmit={handleSubmit}
          onKeyDown={submitOnEnter}
          className="flex flex-col gap-3"
        >
          <FormField id="room" label="Phòng" error={errors.room?.message}>
            <Input
              id="room"
              placeholder="Ví dụ: A2-301"
              maxLength={MAX_ROOM_LENGTH}
              {...form.register("room")}
            />
          </FormField>
        </form>

        <DialogFooter>
          <Button variant="outline" size="sm" onClick={() => handleOpenChange(false)}>
            Hủy
          </Button>
          {/* Khóa nút khi đang gửi để chặn tạo trùng */}
          <Button type="submit" form="create-room-request" size="sm" disabled={create.isPending}>
            {create.isPending ? "Đang gửi..." : "Gửi yêu cầu"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
};
