import { Link } from "react-router";
import {
  IconArrowRight,
  IconBook2,
  IconLifebuoy,
  IconMessageCircle,
  IconRocket,
  IconSettings,
  IconTool,
  IconUser,
  IconUsers,
} from "@tabler/icons-react";
import { DOCS, docPath } from "./catalog";

const ROLE_CARDS = [
  {
    id: "nguoi-yeu-cau",
    icon: IconUser,
    title: "Tôi cần hỗ trợ",
    description: "Gửi yêu cầu, theo dõi tiến độ và xác nhận kết quả.",
  },
  {
    id: "ky-thuat-vien",
    icon: IconTool,
    title: "Tôi xử lý công việc",
    description: "Xem lịch hôm nay, xử lý sự cố và quản lý ca trực.",
  },
  {
    id: "dieu-phoi-vien",
    icon: IconUsers,
    title: "Tôi điều phối",
    description: "Tiếp nhận, duyệt và phân công đúng người trong đơn vị.",
  },
  {
    id: "quan-tri",
    icon: IconSettings,
    title: "Tôi quản trị hệ thống",
    description: "Cấu hình chính sách và quản lý tài khoản, phân quyền.",
  },
];

export const DocsOverview = () => (
  <div className="mx-auto max-w-6xl px-5 py-10 sm:px-10 lg:py-14 xl:px-14">
    <div className="mb-10 max-w-2xl">
      <span className="mb-5 inline-flex items-center gap-2 text-sm font-semibold text-(--landing-accent)">
        <IconBook2 size={18} /> Trung tâm hướng dẫn
      </span>
      <h1 className="text-3xl font-bold leading-tight tracking-tight sm:text-4xl lg:text-[42px]">
        Làm quen nhanh.
        <br />
        Làm việc rõ ràng.
      </h1>
      <p className="mt-5 text-base leading-7 text-(--landing-text-dim)">
        Mọi hướng dẫn bạn cần để gửi yêu cầu, xử lý công việc và sử dụng Smart Service Desk trong
        workspace của mình.
      </p>
    </div>
    <Link
      to={docPath("bat-dau")}
      className="group mb-12 flex flex-col gap-5 rounded-[16px] border border-(--landing-accent)/20 bg-(--landing-accent-soft) p-6 sm:flex-row sm:items-center sm:p-7"
    >
      <span className="grid size-12 shrink-0 place-items-center rounded-[12px] bg-(--landing-surface) text-(--landing-accent)">
        <IconRocket size={25} stroke={1.6} />
      </span>
      <div className="flex-1">
        <h2 className="text-xl font-semibold">Bắt đầu nhanh</h2>
        <p className="mt-1.5 text-sm leading-6 text-(--landing-text-dim)">
          Đăng nhập, chọn workspace và thực hiện thao tác đầu tiên.
        </p>
      </div>
      <span className="flex items-center gap-2 text-sm font-semibold text-(--landing-accent)">
        Mở hướng dẫn{" "}
        <IconArrowRight size={18} className="transition-transform group-hover:translate-x-1" />
      </span>
    </Link>
    <section aria-labelledby="roles-heading">
      <h2 id="roles-heading" className="text-xl font-semibold tracking-tight">
        Hướng dẫn dành cho bạn
      </h2>
      <p className="mt-2 text-sm text-(--landing-text-dim)">
        Chọn vai trò để đi thẳng vào công việc của mình.
      </p>
      <div className="mt-5 grid gap-4 sm:grid-cols-2">
        {ROLE_CARDS.map((card) => (
          <Link key={card.id} to={docPath(card.id)} className="docs-overview-card group">
            <card.icon size={23} stroke={1.6} className="mb-5 text-(--landing-accent)" />
            <h3 className="flex items-center justify-between gap-3 font-semibold">
              {card.title}
              <IconArrowRight
                size={17}
                className="text-(--landing-text-dim) transition-transform group-hover:translate-x-1"
              />
            </h3>
            <p className="mt-2 text-sm leading-6 text-(--landing-text-dim)">{card.description}</p>
          </Link>
        ))}
      </div>
    </section>
    <section aria-labelledby="explore-heading" className="mt-12">
      <h2 id="explore-heading" className="text-xl font-semibold tracking-tight">
        Tra cứu theo nhu cầu
      </h2>
      <div className="mt-5 divide-y divide-(--landing-border)">
        {(
          [
            ["tai-lieu", IconBook2],
            ["hoi-dap", IconMessageCircle],
            ["cai-dat", IconSettings],
            ["su-co", IconLifebuoy],
          ] as const
        ).map(([id, Icon]) => {
          const article = DOCS.find((item) => item.id === id);
          return (
            article && (
              <Link key={id} to={docPath(id)} className="group flex items-center gap-4 py-5">
                <Icon size={21} stroke={1.6} className="shrink-0 text-(--landing-text-dim)" />
                <span className="flex-1">
                  <span className="text-sm font-semibold group-hover:text-(--landing-accent)">
                    {article.title}
                  </span>
                  <span className="mt-1 block text-sm leading-6 text-(--landing-text-dim)">
                    {article.description}
                  </span>
                </span>
                <IconArrowRight size={18} className="shrink-0 text-(--landing-text-dim)" />
              </Link>
            )
          );
        })}
      </div>
    </section>
    <div className="mt-10 border-t border-(--landing-border) pt-6 text-xs leading-6 text-(--landing-text-dim)">
      Hướng dẫn sử dụng · Cập nhật 31/10/2026 · Chức năng hiển thị theo quyền và cấu hình của đơn
      vị.
    </div>
  </div>
);
