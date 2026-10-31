import { useState } from "react";
import { Link } from "react-router";
import { cn } from "cn";
import { APP_HOME } from "../links";
import { Reveal } from "../Reveal";

const FAQS = [
  {
    q: "Tôi có thể dùng Smart Service Desk để làm gì?",
    a: "Bạn có thể gửi ticket hỗ trợ CNTT, yêu cầu đặt phòng, theo dõi tiến độ và tra cứu tài liệu nội bộ. Với ticket, điều phối viên tiếp nhận, duyệt và giao việc; kỹ thuật viên xử lý rồi người gửi xác nhận kết quả.",
  },
  {
    q: "Có thể sửa hoặc hủy yêu cầu đã gửi không?",
    a: "Chủ yêu cầu có thể sửa hoặc hủy khi trạng thái hiện tại còn cho phép. Yêu cầu đặt phòng chỉ được sửa hoặc hủy trước khi duyệt. Với ticket đã duyệt, người gửi có thể đóng khi không còn nhu cầu xử lý. Các thao tác hủy và đóng đều có bước xác nhận và lưu lý do.",
  },
  {
    q: "Có bắt buộc phải bật AI không?",
    a: "Không. Hệ thống có chế độ không dùng AI để phân loại và xử lý thủ công. Khi AI được bật, gợi ý hỗ trợ điều phối viên xem xét loại sự cố và ưu tiên; kết quả duyệt và phân công vẫn do người có quyền quyết định.",
  },
  {
    q: "Vì sao mỗi tài khoản thấy màn hình và thao tác khác nhau?",
    a: "Quyền được cấp theo vai trò và workspace. Người yêu cầu chủ yếu theo dõi yêu cầu của mình; kỹ thuật viên xử lý việc được giao; điều phối viên tiếp nhận, duyệt, giao việc và theo dõi số liệu trong phạm vi được cấp.",
  },
  {
    q: "Làm thế nào để bắt đầu sử dụng?",
    a: "Chọn Vào ứng dụng, đăng nhập bằng tài khoản được quản trị viên cấp và chọn workspace của bạn. Trang chủ có lối tắt tới yêu cầu, công việc và kho tri thức theo quyền hiện có. Mục Tài liệu hướng dẫn riêng cho từng vai trò.",
  },
];

interface FaqItemProps {
  item: (typeof FAQS)[number];
  isOpen: boolean;
  onToggle: () => void;
}

const FaqItem = ({ item, isOpen, onToggle }: FaqItemProps) => {
  return (
    <div className="border-b border-(--landing-border)">
      <button
        onClick={onToggle}
        aria-expanded={isOpen}
        className="flex w-full items-center justify-between gap-4 py-5 text-left text-[17px] font-bold text-(--landing-text) transition-colors hover:text-(--landing-accent)"
      >
        {item.q}
        <span className="relative size-5 shrink-0">
          <span
            className={cn(
              "absolute top-1/2 left-1/2 h-3.5 w-0.5 -translate-x-1/2 -translate-y-1/2 bg-current transition-all duration-200",
              isOpen && "rotate-90 opacity-0",
            )}
          />
          <span className="absolute top-1/2 left-1/2 h-0.5 w-3.5 -translate-x-1/2 -translate-y-1/2 bg-current" />
        </span>
      </button>
      {/* Mở/đóng bằng grid-rows 0fr → 1fr, không cần đo chiều cao bằng JS */}
      <div
        className={cn(
          "grid transition-[grid-template-rows] duration-400 ease-in-out",
          isOpen ? "grid-rows-[1fr]" : "grid-rows-[0fr]",
        )}
      >
        <div className="overflow-hidden">
          <div className="pb-5 text-base leading-relaxed text-(--landing-text-dim)">{item.a}</div>
        </div>
      </div>
    </div>
  );
};

export const Faq = () => {
  const [openIndex, setOpenIndex] = useState<number | null>(null);

  return (
    <section className="bg-white px-6 py-20">
      <div className="mx-auto max-w-300">
        <Reveal
          as="h2"
          className="mb-8 text-center text-[clamp(28px,4vw,52px)] font-extrabold tracking-tight text-(--landing-text)"
        >
          Câu hỏi thường gặp
        </Reveal>
        <Reveal delay={80} className="mb-12 flex flex-wrap items-center justify-center gap-6">
          <div className="text-right">
            <p className="text-[17px] font-bold text-(--landing-text)">Còn thắc mắc?</p>
            <p className="text-base text-(--landing-text-dim)">
              Bắt đầu với hướng dẫn theo vai trò của bạn
            </p>
          </div>
          <Link
            to={APP_HOME}
            className="rounded-lg bg-(--landing-accent) px-7 py-3 text-base font-bold text-white transition-opacity hover:opacity-90"
          >
            Vào ứng dụng
          </Link>
        </Reveal>

        <Reveal delay={120} className="mx-auto max-w-195">
          {FAQS.map((item, i) => (
            <FaqItem
              key={item.q}
              item={item}
              isOpen={openIndex === i}
              onToggle={() => setOpenIndex(openIndex === i ? null : i)}
            />
          ))}
        </Reveal>
      </div>
    </section>
  );
};
