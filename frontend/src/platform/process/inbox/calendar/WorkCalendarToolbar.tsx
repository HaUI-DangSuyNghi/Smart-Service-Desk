import { IconRefresh } from "@tabler/icons-react";
import { Button } from "@/platform/shared/ui/button";
import { Input } from "@/platform/shared/ui/input";
import { PageToolbar } from "@/platform/shared/ui/page";
import { SelectField } from "@/platform/shared/ui/SelectField";
import { WORK_STATE_LABELS } from "../../work/workLabels";
import type { CalendarState } from "./calendarModel";

interface Props {
  search: string;
  state: CalendarState;
  isFetching: boolean;
  onSearch: (value: string) => void;
  onStateChange: (value: CalendarState) => void;
  onRefresh: () => void;
}

export const WorkCalendarToolbar = ({
  search,
  state,
  isFetching,
  onSearch,
  onStateChange,
  onRefresh,
}: Props) => (
  <PageToolbar
    start={<span className="text-crm-sm font-medium">Hạn hoàn thành</span>}
    end={
      <div className="flex w-full min-w-0 flex-wrap items-center gap-2">
        <Input
          aria-label="Tìm công việc"
          placeholder="Tìm công việc…"
          value={search}
          onChange={(event) => onSearch(event.target.value)}
          className="h-9 min-w-0 flex-1 sm:w-56 sm:flex-none md:h-7"
        />
        <SelectField
          aria-label="Trạng thái công việc"
          placeholder="Mọi trạng thái"
          options={Object.entries(WORK_STATE_LABELS).map(([value, label]) => ({ value, label }))}
          value={state}
          onValueChange={(value) => onStateChange(value as CalendarState)}
          className="min-h-9 md:min-h-7"
        />
        <Button
          variant="ghost"
          size="icon-sm"
          className="size-9 md:size-7"
          aria-label="Làm mới lịch"
          disabled={isFetching}
          onClick={onRefresh}
        >
          <IconRefresh size={16} />
        </Button>
      </div>
    }
  />
);
