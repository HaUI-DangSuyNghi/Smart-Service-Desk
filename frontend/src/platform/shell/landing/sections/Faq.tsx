import { useState } from "react";
import { Link } from "react-router";
import { cn } from "cn";
import { APP_HOME } from "../links";
import { Reveal } from "../Reveal";

const FAQS = [
  {
    q: "Smart Service Desk làm gì?",
    a: "Smart Service Desk số hoá việc hỗ trợ CNTT trong trường: người dùng tạo ticket, kỹ thuật viên nhận và xử lý theo quy trình BPMN, người yêu cầu xác nhận khi xong. Toàn bộ lịch sử được ghi lại, AI gợi ý phân loại để xử lý nhanh hơn.",
  },
  {
    q: "DX-OS 3 tầng nghĩa là gì?",
    a: "Tầng 1 Open-Core là các dịch vụ lõi chạy ngầm (Keycloak, APISIX, Flowable, PostgreSQL). Tầng 2 H-P-D-I là năng lực số dùng chung: con người, quy trình, dữ liệu, trí tuệ. Tầng 3 là ứng dụng nghiệp vụ — Smart Service Desk là một ứng dụng như vậy, và ứng dụng khác cũng cắm vào được mà không sửa nền tảng.",
  },
  {
    q: "Gợi ý của AI có đáng tin không?",
    a: "Mỗi gợi ý kèm luật đã khớp, đoạn văn bản làm căn cứ và cờ cần xem lại — không phải hộp đen. Gợi ý không bao giờ tự áp dụng vào ticket. Khi dịch vụ AI ngừng, hệ thống trả trạng thái DEGRADED và ticket vẫn xử lý thủ công bình thường.",
  },
  {
    q: "Dữ liệu giữa các đơn vị có tách biệt không?",
    a: "Có. Mỗi đơn vị là một workspace, vai trò gắn theo từng workspace. Backend kiểm quyền cho mọi request; người ngoài workspace không thấy ticket, tài liệu hay số liệu của workspace đó.",
  },
  {
    q: "Làm sao để dùng thử?",
    a: "Chạy scripts/setup.ps1 rồi scripts/start.ps1 để dựng toàn bộ stack bằng Docker Compose, sau đó đăng nhập bằng tài khoản demo user1–user7. Mật khẩu được sinh tự động vào biến DEMO_PASSWORD trong file .env.",
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
          Câu hỏi Thường gặp
        </Reveal>
        <Reveal delay={80} className="mb-12 flex flex-wrap items-center justify-center gap-6">
          <div className="text-right">
            <p className="text-[17px] font-bold text-(--landing-text)">Còn thắc mắc?</p>
            <p className="text-base text-(--landing-text-dim)">
              Xem thêm tài liệu kỹ thuật của dự án
            </p>
          </div>
          <Link
            to={APP_HOME}
            className="rounded-lg bg-(--landing-accent) px-7 py-3 text-base font-bold text-white transition-opacity hover:opacity-90"
          >
            Dùng thử demo
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
