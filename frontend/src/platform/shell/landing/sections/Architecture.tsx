import { ArrowDown, ArrowRight } from "lucide-react";
import { cn } from "cn";
import { Reveal } from "../Reveal";

// Mỗi hàng là một tầng, từ trên xuống: ứng dụng → H-P-D-I → Open-Core
const FLOW_ROWS = [
  [
    ["apps.servicedesk", "Tiếp nhận, duyệt, giao và xử lý ticket"],
    ["apps.roomrequest", "Gửi, duyệt và theo dõi yêu cầu đặt phòng"],
  ],
  [
    ["hpdi.human", "Người dùng, workspace, vai trò"],
    ["hpdi.process", "Công việc, lịch, tiến độ và thao tác yêu cầu"],
    ["hpdi.data", "Tri thức, metadata, thống kê"],
    ["hpdi.intelligence", "Phân loại và hỏi đáp có AI hỗ trợ"],
  ],
  [
    ["core.identity", "Keycloak, JWT, quyền theo workspace"],
    ["core.workflow", "Flowable BPMN, lịch sử"],
    ["core.data", "Metadata, file, provenance"],
    ["core.audit", "Nhật ký cùng transaction"],
  ],
];

const Connector = ({ direction = "right" }: { direction?: "right" | "down" }) => {
  const Icon = direction === "right" ? ArrowRight : ArrowDown;
  return (
    // Mũi tên ngang chỉ có nghĩa khi cả hàng nằm trên 1 dòng (4 thẻ 200px + mũi tên cần ~970px);
    // màn hẹp hơn thẻ xuống dòng, mũi tên ngang sẽ chỉ lung tung
    <div
      className={cn(
        "shrink-0 items-center justify-center",
        direction === "right" ? "hidden xl:flex" : "flex",
      )}
    >
      <span className="grid size-8 shrink-0 place-items-center rounded-full bg-(--landing-accent-soft) text-(--landing-accent)">
        <Icon className="size-4" />
      </span>
    </div>
  );
};

export const Architecture = () => {
  return (
    <section
      id="architecture"
      className="border-t border-(--landing-border) bg-white px-6 py-20 lg:px-8"
    >
      <div className="mx-auto max-w-360">
        <Reveal className="mb-14 max-w-190">
          <div className="mb-3.5 text-sm font-bold tracking-[0.08em] text-(--landing-accent) uppercase">
            Kiến trúc
          </div>
          <h2 className="mb-3.5 text-[clamp(34px,4vw,50px)] leading-tight font-extrabold tracking-tight text-(--landing-text)">
            Hai ứng dụng, một nền tảng dùng chung
          </h2>
          <p className="text-xl leading-relaxed text-(--landing-text-dim)">
            Service Desk và Đặt phòng cùng sử dụng năng lực con người, quy trình, dữ liệu và AI của
            DX-OS. Nghiệp vụ được tách khỏi các dịch vụ lõi để có thể bổ sung ứng dụng mà vẫn dùng
            chung đăng nhập, công việc và lịch sử xử lý.
          </p>
        </Reveal>

        <Reveal className="flex flex-col items-center gap-3">
          {FLOW_ROWS.map((row, i) => (
            <div key={i} className="contents">
              <div className="flex w-full flex-wrap items-stretch justify-center gap-3">
                {row.map(([name, desc], j) => (
                  <div key={name} className="contents">
                    <div className="min-w-50 flex-1 basis-50 rounded-2xl border border-(--landing-border) bg-(--landing-bg-soft) p-5 shadow-sm transition-all duration-300 hover:-translate-y-1 hover:border-(--landing-accent)/40 hover:shadow-md">
                      <b className="block font-mono text-[15px] font-bold text-(--landing-text)">
                        {name}
                      </b>
                      <span className="text-sm text-(--landing-text-dim)">{desc}</span>
                    </div>
                    {j < row.length - 1 && <Connector direction="right" />}
                  </div>
                ))}
              </div>
              {i < FLOW_ROWS.length - 1 && <Connector direction="down" />}
            </div>
          ))}
        </Reveal>
      </div>
    </section>
  );
};
