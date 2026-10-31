import type { ComponentType } from "react";
import {
  IconChecks,
  IconDots,
  IconFileText,
  IconHandGrab,
  IconPaperclip,
  IconSend,
  IconUserShare,
} from "@tabler/icons-react";
import type { ActivityKind } from "./describeActivity";

/** Icon theo loại thao tác, dùng chung cho "Hoạt động của tôi" và trang Nhật ký. */
export const ACTIVITY_ICONS: Record<
  ActivityKind,
  ComponentType<{ size?: number; stroke?: number; className?: string }>
> = {
  request: IconSend,
  claim: IconHandGrab,
  assign: IconUserShare,
  complete: IconChecks,
  document: IconFileText,
  file: IconPaperclip,
  other: IconDots,
};
