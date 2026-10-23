import { ArrowDown, ArrowRight } from "lucide-react";
import { Reveal } from "../Reveal";

// Mỗi hàng là một tầng, từ trên xuống: ứng dụng → H-P-D-I → Open-Core
const FLOW_ROWS = [
  [
    ["apps.servicedesk", "Ticket, quy trình hỗ trợ, thống kê"],
    ["apps.roomrequest", "Ứng dụng thứ hai, chứng minh dùng lại"],
  ],
  [
    ["hpdi.human", "Người dùng, workspace, vai trò"],
    ["hpdi.process", "Hộp việc, nhận việc, timeline"],
    ["hpdi.data", "Tri thức, metadata, thống kê"],
    ["hpdi.intelligence", "AI triage có giải thích"],
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
    <div className="flex shrink-0 items-center justify-center">
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
            Gọi xuống, không gọi ngược
          </h2>
          <p className="text-xl leading-relaxed text-(--landing-text-dim)">
            Modular monolith: một tiến trình Spring Boot, ba tầng là ba package riêng. Ứng dụng chỉ
            dùng H-P-D-I, H-P-D-I chỉ dùng Open-Core — ranh giới được kiểm tự động mỗi lần build.
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
