import { roleLabel, roleTone } from "@/platform/human/workspace";
import { Tag } from "@/platform/shared/ui/Tag";
import { groupRole } from "./display";

/** Nhóm được nhận việc ("lab-a:agent" → tag "Kỹ thuật viên"); không có nhóm là bước giao thẳng người tạo. */
export const CandidateGroups = ({ groups }: { groups: readonly string[] }) => {
  if (groups.length === 0) return <span className="text-crm-text-3">Người tạo yêu cầu</span>;

  return (
    <span className="flex flex-wrap gap-1">
      {groups.map((group) => (
        <Tag key={group} tone={roleTone(groupRole(group))}>
          {roleLabel(groupRole(group))}
        </Tag>
      ))}
    </span>
  );
};
