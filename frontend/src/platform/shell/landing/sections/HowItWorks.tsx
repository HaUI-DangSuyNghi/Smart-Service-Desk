import { AppWindow, Blocks, Server } from "lucide-react";
import { Reveal } from "../Reveal";

const PILLARS = [
  {
    icon: Server,
    title: "01. Gửi và theo dõi yêu cầu",
    desc: "Mô tả sự cố CNTT hoặc gửi yêu cầu đặt phòng. Xem trạng thái và tiến độ trên trang chi tiết; trao đổi trên ticket khi cần làm rõ thông tin sự cố.",
  },
  {
    icon: Blocks,
    title: "02. Tiếp nhận, duyệt và giao",
    desc: "Điều phối viên kiểm tra thông tin, xem gợi ý phân loại nếu AI được bật, xác định ưu tiên và giao ticket cho kỹ thuật viên phù hợp.",
  },
  {
    icon: AppWindow,
    title: "03. Xử lý và xác nhận",
    desc: "Kỹ thuật viên bắt đầu, cập nhật tình trạng và báo xong. Người gửi xác nhận kết quả hoặc yêu cầu xử lý lại nếu sự cố chưa được giải quyết.",
  },
];

export const HowItWorks = () => {
  return (
    <section id="how" className="bg-(--landing-bg-soft) px-6 py-20 lg:px-8">
      <div className="mx-auto max-w-360">
        <Reveal className="mx-auto mb-14 max-w-250 text-center">
          <h2 className="mb-5 text-[clamp(34px,4.5vw,58px)] leading-[1.1] font-extrabold tracking-tight text-(--landing-text)">
            Từ lúc gửi đến khi giải quyết
          </h2>
          <p className="text-xl leading-relaxed text-(--landing-text-dim)">
            Mỗi bước đều có người phụ trách và lịch sử để tra cứu. Cả người gửi, điều phối viên và
            kỹ thuật viên cùng theo dõi một yêu cầu xuyên suốt quá trình xử lý.
          </p>
        </Reveal>

        <div className="grid grid-cols-1 gap-6 md:grid-cols-3">
          {PILLARS.map((p, i) => (
            <Reveal key={p.title} delay={i * 80}>
              <div className="h-full rounded-[20px] border border-(--landing-border) bg-[#f9f9fa] p-6 transition-all lg:p-10 lg:pb-8 duration-300 hover:-translate-y-1 hover:shadow-[0_4px_24px_rgba(0,0,0,0.14)]">
                <div className="mb-5 grid size-12 place-items-center rounded-xl border border-(--landing-border) bg-white text-(--landing-text)">
                  <p.icon className="size-6" />
                </div>
                <h3 className="mb-2.5 text-xl font-bold text-(--landing-text)">{p.title}</h3>
                <p className="text-base leading-relaxed text-(--landing-text-dim)">{p.desc}</p>
              </div>
            </Reveal>
          ))}
        </div>
      </div>
    </section>
  );
};
