import { Reveal } from "../Reveal";

const ROLES = [
  {
    tag: "Requester",
    title: "Người yêu cầu",
    items: [
      "Tạo ticket hỗ trợ CNTT mới",
      "Theo dõi tiến độ ticket của mình",
      "Xác nhận đã xong hoặc yêu cầu làm lại",
    ],
  },
  {
    tag: "Agent",
    title: "Kỹ thuật viên",
    items: [
      "Nhận việc từ hàng chờ của nhóm",
      "Xử lý và đánh dấu đã giải quyết",
      "Tham khảo gợi ý phân loại của AI",
    ],
  },
  {
    tag: "Coordinator",
    title: "Điều phối viên",
    items: [
      "Xem toàn bộ ticket trong workspace",
      "Theo dõi thống kê và lịch sử quy trình",
      "Nhận việc khi có thêm vai trò kỹ thuật viên",
    ],
  },
];

export const Roles = () => {
  return (
    <section
      id="roles"
      className="border-t border-(--landing-border) bg-(--landing-bg-soft) px-6 py-20 lg:px-8"
    >
      <div className="mx-auto max-w-360">
        <Reveal className="mb-14 max-w-190">
          <div className="mb-3.5 text-sm font-bold tracking-[0.08em] text-(--landing-accent) uppercase">
            Phân quyền
          </div>
          <h2 className="mb-3.5 text-[clamp(34px,4vw,50px)] leading-tight font-extrabold tracking-tight text-(--landing-text)">
            Ba vai trò, một luồng làm việc
          </h2>
          <p className="text-xl leading-relaxed text-(--landing-text-dim)">
            Vai trò gắn theo từng workspace. Mỗi người chỉ thấy đúng dữ liệu của workspace mình, và
            backend luôn tự kiểm quyền.
          </p>
        </Reveal>

        <div className="grid grid-cols-1 gap-6 sm:grid-cols-3">
          {ROLES.map((role, i) => (
            <Reveal key={role.tag} delay={i * 80}>
              <div className="h-full rounded-[20px] border border-(--landing-border) bg-white p-8 transition-all duration-300 hover:-translate-y-1 hover:shadow-[0_4px_24px_rgba(0,0,0,0.1)]">
                <span className="mb-2.5 inline-block text-xs font-bold tracking-[0.04em] text-(--landing-accent) uppercase">
                  {role.tag}
                </span>
                <h3 className="text-xl font-bold tracking-tight text-(--landing-text)">
                  {role.title}
                </h3>
                <ul className="mt-3.5 list-disc pl-4.5 text-base text-(--landing-text-dim)">
                  {role.items.map((item) => (
                    <li key={item} className="mb-1.5">
                      {item}
                    </li>
                  ))}
                </ul>
              </div>
            </Reveal>
          ))}
        </div>
      </div>
    </section>
  );
};
