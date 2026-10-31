import { IconPlus } from "@tabler/icons-react";
import { Button } from "@/platform/shared/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuLabel,
  DropdownMenuRadioGroup,
  DropdownMenuRadioItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/platform/shared/ui/dropdown-menu";

interface Props {
  documents: readonly { id: string; title: string }[];
  total: number;
  value: string;
  disabled: boolean;
  onChange: (value: string) => void;
}

export const ChatScopePicker = ({ documents, total, value, disabled, onChange }: Props) => (
  <DropdownMenu>
    <DropdownMenuTrigger asChild>
      <Button
        type="button"
        variant="ghost"
        size="icon"
        className="size-9 shrink-0 rounded-full text-crm-text-2"
        disabled={disabled}
        aria-label="Chọn phạm vi tài liệu"
        title="Chọn phạm vi tài liệu"
      >
        <IconPlus size={20} />
      </Button>
    </DropdownMenuTrigger>
    <DropdownMenuContent side="top" align="start" className="w-80 max-w-[calc(100vw-4rem)]">
      <DropdownMenuLabel className="text-crm-sm text-crm-text-2">
        Phạm vi tài liệu
      </DropdownMenuLabel>
      <DropdownMenuSeparator />
      <DropdownMenuRadioGroup value={value} onValueChange={onChange}>
        <DropdownMenuRadioItem value="" className="min-h-9 text-crm-sm">
          Tất cả tài liệu ({total})
        </DropdownMenuRadioItem>
        {documents.map((document) => (
          <DropdownMenuRadioItem
            key={document.id}
            value={document.id}
            className="min-h-9 text-crm-sm"
          >
            <span className="truncate">{document.title}</span>
          </DropdownMenuRadioItem>
        ))}
      </DropdownMenuRadioGroup>
    </DropdownMenuContent>
  </DropdownMenu>
);
