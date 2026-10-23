import { IconCheck, IconChevronDown, IconLogout, IconUserCircle } from "@tabler/icons-react";
import { useNavigate } from "react-router";
import { cn } from "cn";
import { useAuth } from "@/platform/human/auth";
import { useCurrentWorkspace, useMe } from "@/platform/human/workspace";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/platform/shared/ui/dropdown-menu";

interface Props {
  collapsed: boolean;
}

/** Đầu sidebar kiểu Twenty: logo chữ cái + tên workspace; dropdown đổi workspace và đăng xuất. */
export const WorkspaceSwitcher = ({ collapsed }: Props) => {
  const { workspaces, current, currentName, select } = useCurrentWorkspace();
  const { data: me } = useMe();
  const { logout } = useAuth();
  const navigate = useNavigate();
  const name = currentName ?? "Chưa có workspace";

  return (
    <DropdownMenu>
      <DropdownMenuTrigger
        title={collapsed ? name : undefined}
        className={cn(
          "flex h-9 min-w-0 items-center gap-2 rounded-crm-sm px-1 text-crm-text outline-none md:h-7",
          "transition-colors duration-100 hover:bg-crm-hover data-[state=open]:bg-crm-hover",
          collapsed && "w-8 justify-center px-0",
        )}
      >
        <span className="grid size-[18px] shrink-0 place-items-center rounded-crm-sm bg-crm-text text-crm-xs font-semibold text-white">
          {name.charAt(0).toUpperCase()}
        </span>
        {!collapsed && (
          <>
            <span className="truncate font-medium">{name}</span>
            <IconChevronDown size={14} stroke={2} className="shrink-0 text-crm-text-3" />
          </>
        )}
      </DropdownMenuTrigger>

      <DropdownMenuContent align="start" className="min-w-56">
        {workspaces.length > 0 && (
          <>
            <DropdownMenuLabel className="text-crm-xs font-normal text-crm-text-3">
              Workspace
            </DropdownMenuLabel>
            {workspaces.map((item) => (
              <DropdownMenuItem key={item.workspace} onSelect={() => select(item.workspace)}>
                <span className="flex flex-1 flex-col">
                  <span>{item.name}</span>
                  {item.name !== item.workspace && (
                    <span className="text-crm-xs text-crm-text-3">{item.workspace}</span>
                  )}
                </span>
                {item.workspace === current && <IconCheck size={14} stroke={2} />}
              </DropdownMenuItem>
            ))}
            <DropdownMenuSeparator />
          </>
        )}
        <DropdownMenuLabel className="truncate text-crm-xs font-normal text-crm-text-3">
          {me?.username}
        </DropdownMenuLabel>
        <DropdownMenuItem onSelect={() => navigate("/profile")}>
          <IconUserCircle size={16} stroke={2} />
          Tài khoản
        </DropdownMenuItem>
        <DropdownMenuItem onSelect={() => void logout()}>
          <IconLogout size={16} stroke={2} />
          Đăng xuất
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  );
};
