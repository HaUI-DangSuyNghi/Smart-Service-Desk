import { CheckCircle2, Eye, TrendingUp } from "lucide-react";
import { Reveal } from "../Reveal";

const FEATURES = [
  {
    icon: CheckCircle2,
    title: "Nhìn được việc còn chờ",
    desc: "Theo dõi yêu cầu mới, đang xử lý, chờ xác nhận và quá hạn để biết khâu nào cần được quan tâm.",
  },
  {
    icon: Eye,
    title: "Đọc lại diễn biến xử lý",
    desc: "Tiến độ và lịch sử thay đổi cho biết ai đã thực hiện, vào thời điểm nào và vì sao yêu cầu thay đổi.",
  },
  {
    icon: TrendingUp,
    title: "Cập nhật tới người liên quan",
    desc: "Các thay đổi công việc được cập nhật qua thông báo; những trạng thái quan trọng có thể được gửi qua email khi đã cấu hình.",
  },
];

const STATS = [
  ["3", "tầng kiến trúc DX-OS"],
  ["4", "năng lực H-P-D-I"],
  ["2", "ứng dụng: hỗ trợ CNTT và đặt phòng"],
  ["3", "vai trò nghiệp vụ"],
];

export const StatsSection = () => {
  return (
    <section className="bg-(--landing-bg-soft) px-6 py-16 lg:px-8">
      <Reveal className="mx-auto max-w-360 overflow-hidden rounded-[28px] border border-(--landing-border) bg-white p-8 sm:p-12">
        <div className="grid items-center gap-12 lg:grid-cols-2">
          <div className="min-w-0 overflow-hidden rounded-2xl border border-(--landing-border)">
            <img
              src="/dashboard.png"
              alt="Dashboard tổng hợp số liệu yêu cầu, tiến độ xử lý và thống kê ticket"
              width={1912}
              height={868}
              loading="lazy"
              decoding="async"
              className="block h-auto w-full"
            />
          </div>
          <div>
            <h3 className="mb-8 text-[clamp(34px,4vw,50px)] leading-[1.07] font-extrabold tracking-tight text-(--landing-text)">
              Nắm tiến độ, chủ động điều phối
            </h3>
            <div className="flex flex-col gap-7">
              {FEATURES.map((f) => (
                <div key={f.title} className="flex items-start gap-4">
                  <div className="mt-1 grid size-12 shrink-0 place-items-center rounded-xl bg-(--landing-text)">
                    <f.icon className="size-6 text-white" />
                  </div>
                  <div>
                    <h4 className="mb-1.5 text-base font-bold tracking-wide text-(--landing-text) uppercase">
                      {f.title}
                    </h4>
                    <p className="text-base leading-relaxed text-(--landing-text-dim)">{f.desc}</p>
                  </div>
                </div>
              ))}
            </div>
          </div>
        </div>

        {/* Kẻ dọc chỉ khi 4 số nằm 1 hàng; lưới 2×2 trên điện thoại mà kẻ thì ô đầu hàng 2 bị dính vạch */}
        <div className="mt-10 grid grid-cols-2 gap-y-8 border-t border-(--landing-border) pt-8 sm:grid-cols-4 sm:divide-x sm:divide-(--landing-border)">
          {STATS.map(([value, label]) => (
            <div key={label} className="px-4 text-center">
              <div className="mb-1.5 text-[clamp(28px,3vw,42px)] leading-none font-black tracking-tight text-(--landing-text)">
                {value}
              </div>
              <div className="text-[13px] font-medium text-(--landing-text-dim)">{label}</div>
            </div>
          ))}
        </div>
      </Reveal>
    </section>
  );
};
