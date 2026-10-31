import { IconLock, IconWorld } from "@tabler/icons-react";
import { Tag } from "@/platform/shared/ui/Tag";
import { isInternalKnowledge } from "./knowledgeAccess";

export const KnowledgeAccessTag = ({ value }: { value: string }) => {
  const isInternal = isInternalKnowledge(value);
  const Icon = isInternal ? IconLock : IconWorld;
  return (
    <Tag tone={isInternal ? "gray" : "blue"}>
      <Icon size={12} />
      {isInternal ? "Nội bộ" : "Công khai trong workspace"}
    </Tag>
  );
};
